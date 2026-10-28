#!/usr/bin/env python3
"""
SnoopAR: Monte Carlo Multi-Seed Benchmark & Validation Harness
==============================================================
Evaluates localization accuracy under realistic RF conditions:
- Rayleigh fast fading (mean 0 dB, deep nulls)
- User body-blocking attenuation (8 dB torso loss)
- Unknown / mismatched transmit power A and path-loss exponent n
- Realistic time budget: 20 coarse readings + 15 close sweep readings (1 per spot)
"""

import sys
import math
import numpy as np

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")


class RealisticRFChannel:
    """
    Simulates realistic 2.4 GHz propagation around vehicles:
    - Log-distance path loss
    - Asymmetric Rayleigh fading (zero mean, deep nulls)
    - Body-blocking attenuation when user faces away
    """
    def __init__(self, target_pos, true_A=-59.0, true_n=2.7):
        self.target_pos = np.array(target_pos, dtype=np.float64)
        self.true_A = true_A
        self.true_n = true_n

    def sample_rssi(self, observer_pos, camera_gaze_vec):
        disp = self.target_pos - observer_pos
        dist = max(np.linalg.norm(disp), 0.05)
        
        # 1. Base log-distance
        base_rssi = self.true_A - 10.0 * self.true_n * math.log10(dist)
        
        # 2. Rayleigh fading in dB:
        # E[10*log10(-ln U)] = -2.507 dB. Adding +2.507 sets expected value to exactly 0.0 dB.
        u = np.random.uniform(1e-4, 0.9999)
        rayleigh_fade_db = 10.0 * math.log10(-math.log(u)) + 2.507
        
        # 3. Body blocking: if user body is between phone and target
        unit_disp = disp / dist
        cos_angle = np.dot(camera_gaze_vec, unit_disp)
        body_loss = 0.0
        if cos_angle < 0.0:  # Target is behind phone, through user's torso
            body_loss = 8.0 * ((1.0 - cos_angle) / 2.0) ** 2
            
        # Additional background Gaussian shadow noise
        shadow_noise = np.random.normal(0.0, 2.0)
        
        return base_rssi + rayleigh_fade_db - body_loss + shadow_noise


class NaiveParticleFilter:
    """Baseline filter with unconstrained uniform A and Gaussian likelihood."""
    def __init__(self, num_particles=6000, bound=2.5):
        self.N = num_particles
        self.particles = np.zeros((self.N, 5), dtype=np.float64)
        self.particles[:, 0] = np.random.uniform(-bound, bound, self.N)
        self.particles[:, 1] = np.random.uniform(-bound, bound, self.N)
        self.particles[:, 2] = np.random.uniform(-bound*0.5, bound*0.5, self.N)
        self.particles[:, 3] = np.random.uniform(-75.0, -45.0, self.N)
        self.particles[:, 4] = np.random.uniform(1.8, 3.8, self.N)
        self.weights = np.ones(self.N, dtype=np.float64) / self.N
        self.sigma = 4.0

    def update(self, obs_pos, rssi):
        dx = self.particles[:, 0] - obs_pos[0]
        dy = self.particles[:, 1] - obs_pos[1]
        dz = self.particles[:, 2] - obs_pos[2]
        dist = np.maximum(np.sqrt(dx*dx + dy*dy + dz*dz), 0.05)
        
        expected = self.particles[:, 3] - 10.0 * self.particles[:, 4] * np.log10(dist)
        residual = rssi - expected
        likelihood = np.exp(-0.5 * (residual / self.sigma) ** 2)
        
        self.weights *= likelihood
        total = np.sum(self.weights)
        if total < 1e-12:
            self.weights = np.ones(self.N, dtype=np.float64) / self.N
        else:
            self.weights /= total
            
        if (1.0 / np.sum(self.weights ** 2)) < (self.N / 2):
            idx = np.random.choice(self.N, size=self.N, p=self.weights)
            self.particles = self.particles[idx]
            self.particles[:, 0:3] += np.random.normal(0, 0.04, (self.N, 3))
            self.weights = np.ones(self.N, dtype=np.float64) / self.N

    def estimate(self):
        return np.average(self.particles[:, 0:3], axis=0, weights=self.weights)


class MarginalizedGridEstimator:
    """
    Coarse-to-Fine Volumetric Grid with:
    - Closed-form analytical marginalization of A
    - Heavy-tailed Student-t likelihood
    - Torso attenuation compensation
    - ARCore Y-Up coordinates (X=lateral, Y=height, Z=depth)
    """
    def __init__(self, center=(0, 0.5, 0), span=(3.5, 1.6, 3.5), resolution=0.15,
                 prior_A=-59.0, sigma_A=2.0, n_assumed=2.7):
        self.resolution = resolution
        self.prior_A = prior_A
        self.sigma_A = sigma_A
        self.n = n_assumed
        self.nu = 3.0  # Student-t degrees of freedom
        self.sigma_obs = 3.5
        
        xs = np.arange(center[0] - span[0]/2, center[0] + span[0]/2, resolution)
        ys = np.arange(center[1] - span[1]/2, center[1] + span[1]/2, resolution)
        zs = np.arange(center[2] - span[2]/2, center[2] + span[2]/2, resolution)
        
        gx, gy, gz = np.meshgrid(xs, ys, zs, indexing='ij')
        self.grid_points = np.stack([gx.ravel(), gy.ravel(), gz.ravel()], axis=-1)
        self.observations = []

    def update(self, obs_pos, gaze_vec, rssi):
        self.observations.append((obs_pos, gaze_vec, rssi))

    def evaluate_grid(self):
        if not self.observations:
            return np.zeros(len(self.grid_points))

        # Vectorized calculation over all grid points
        log_lik = np.zeros(len(self.grid_points), dtype=np.float64)
        K = len(self.observations)

        # Precompute geometry for each observation
        g_all = []
        z_all = []
        for obs_pos, gaze_vec, rssi in self.observations:
            disp = self.grid_points - obs_pos
            dist = np.maximum(np.linalg.norm(disp, axis=1), 0.05)
            path_loss = -10.0 * self.n * np.log10(dist)

            unit_disp = disp / dist[:, None]
            cos_angle = np.dot(unit_disp, gaze_vec)
            body_loss = np.where(cos_angle < 0.0, 8.0 * ((1.0 - cos_angle) / 2.0) ** 2, 0.0)

            g = path_loss - body_loss
            g_all.append(g)
            z_all.append(rssi)

        g_matrix = np.stack(g_all, axis=0) # [K, num_voxels]
        z_vector = np.array(z_all)[:, None] # [K, 1]

        # Analytical closed-form marginalization of A per voxel:
        # Prior A ~ N(mu_A, sigma_A^2)
        prior_prec = 1.0 / (self.sigma_A ** 2)
        obs_prec = 1.0 / (self.sigma_obs ** 2)
        post_prec = prior_prec + K * obs_prec
        post_var = 1.0 / post_prec

        sum_z_minus_g = np.sum(z_vector - g_matrix, axis=0) # [num_voxels]
        A_hat = post_var * (self.prior_A * prior_prec + obs_prec * sum_z_minus_g)

        # Prior penalty on deviation from factory prior
        prior_penalty = -0.5 * ((A_hat - self.prior_A) / self.sigma_A) ** 2
        log_lik += prior_penalty

        # Student-t likelihood
        scale_sq = self.nu * (self.sigma_obs ** 2)
        for k in range(K):
            residual = z_vector[k, 0] - (A_hat + g_matrix[k])
            log_lik += -0.5 * (self.nu + 1.0) * np.log(1.0 + (residual ** 2) / scale_sq)

        return log_lik

    def estimate(self):
        log_lik = self.evaluate_grid()
        max_idx = np.argmax(log_lik)
        peak_val = log_lik[max_idx]

        # Top cluster within 10 nats of peak
        cutoff = peak_val - 10.0
        mask = log_lik >= cutoff
        weights = np.exp(log_lik[mask] - peak_val)
        weights /= np.sum(weights)

        refined_pos = np.sum(self.grid_points[mask] * weights[:, None], axis=0)
        return refined_pos


def run_benchmark():
    num_runs = 30
    true_target_base = np.array([1.10, 0.35, -1.20]) # ARCore Y-up: X=right, Y=height, Z=depth

    print("=" * 80)
    print(" SnoopAR: Monte Carlo Validation (30 Runs under Realistic Conditions)")
    print(" Testing with RANDOMIZED true transmit power (A) and path-loss (n) per seed")
    print(" Time Budget: 20 Coarse + 15 Close readings (Total 35 readings, 1 per spot)")
    print("=" * 80)

    errors_naive = []
    errors_coarse = []
    errors_close = []

    for seed in range(num_runs):
        np.random.seed(seed)

        # Randomize true channel physics per run!
        # True A varies around -59 dBm by +- 4 dB (tag enclosure, orientation, battery)
        true_A = np.random.normal(-59.0, 2.5)
        true_n = np.random.uniform(2.4, 3.0)
        channel = RealisticRFChannel(true_target_base, true_A=true_A, true_n=true_n)

        # --- Stage 1: Coarse Perimeter Arc (20 readings at 1.5 - 2.2m distance) ---
        num_coarse_steps = 20
        angles = np.linspace(-math.pi / 2.5, math.pi / 2.5, num_coarse_steps)

        naive_pf = NaiveParticleFilter(num_particles=5000, bound=2.5)
        coarse_grid = MarginalizedGridEstimator(center=(0.5, 0.5, -0.8), span=(3.5, 1.6, 3.5), resolution=0.15)

        for theta in angles:
            obs_x = 2.0 * math.sin(theta) + 0.2
            obs_y = 0.85 + 0.10 * math.sin(theta * 3.0) # Handheld camera height
            obs_z = -2.0 * math.cos(theta) + 0.5
            obs_pos = np.array([obs_x, obs_y, obs_z])

            gaze = (np.array([0.5, 0.4, -1.0]) - obs_pos)
            gaze /= np.linalg.norm(gaze)

            rssi = channel.sample_rssi(obs_pos, gaze)

            naive_pf.update(obs_pos, rssi)
            coarse_grid.update(obs_pos, gaze, rssi)

        est_naive = naive_pf.estimate()
        est_coarse = coarse_grid.estimate()

        errors_naive.append(np.linalg.norm(est_naive - true_target_base))
        errors_coarse.append(np.linalg.norm(est_coarse - true_target_base))

        # --- Stage 2: Guided Close Sweep along suspect surface (15 readings, 1 per spot) ---
        # User sweeps 35 cm in front of the suspect bumper surface at two heights
        fine_grid = MarginalizedGridEstimator(center=est_coarse, span=(1.4, 0.8, 1.4), resolution=0.05)
        # Inherit coarse observations into the fine grid
        fine_grid.observations = list(coarse_grid.observations)

        num_close_steps = 15
        close_x = np.linspace(0.6, 1.6, num_close_steps)
        for i, cx in enumerate(close_x):
            cy = 0.30 if (i % 2 == 0) else 0.45 # Alternate low and high pass
            cz = -0.85 # 35 cm in front of bumper at Z = -1.20m
            close_pos = np.array([cx, cy, cz])
            gaze = np.array([0.0, 0.0, -1.0]) # Pointing into the bumper

            rssi = channel.sample_rssi(close_pos, gaze)
            fine_grid.update(close_pos, gaze, rssi)

        est_fine = fine_grid.estimate()
        errors_close.append(np.linalg.norm(est_fine - true_target_base))

    def report(name, errors):
        med = np.median(errors) * 100.0
        p25 = np.percentile(errors, 25) * 100.0
        p75 = np.percentile(errors, 75) * 100.0
        w25 = np.mean([1 if e <= 0.25 else 0 for e in errors]) * 100.0
        w35 = np.mean([1 if e <= 0.35 else 0 for e in errors]) * 100.0
        w50 = np.mean([1 if e <= 0.50 else 0 for e in errors]) * 100.0
        print(f"{name:<35} | Med: {med:>5.1f} cm (IQR: {p25:>4.1f}–{p75:>4.1f}) | <25cm: {w25:>3.0f}% | <35cm: {w35:>3.0f}% | <50cm: {w50:>3.0f}%")

    print("-" * 95)
    print(f"{'Method / Strategy':<35} | {'Median Error':<24} | {'<25cm':<7} | {'<35cm':<7} | {'<50cm':<7}")
    print("-" * 95)
    report("A. Naive 5D Particle Filter", errors_naive)
    report("B. Stage 1 Coarse Sweep (20 pkts)", errors_coarse)
    report("C. Stage 2 Close Sweep (35 pkts)", errors_close)
    print("-" * 95)


if __name__ == "__main__":
    run_benchmark()
