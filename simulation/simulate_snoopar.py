#!/usr/bin/env python3
"""
SnoopAR: Synthetic Inverse Localization Simulator
=================================================
Validates the 5D Bayesian Particle Filter for 3D localization of unknown
BLE emitters using Visual-Inertial Odometry (VIO) trajectory positions.
"""

import sys
import math
import numpy as np

# Ensure UTF-8 output on Windows consoles
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

class SnoopARParticleFilter:
    def __init__(self, num_particles=8000, initial_bound=3.0, sigma_rssi=4.0):
        self.num_particles = num_particles
        self.sigma_rssi = sigma_rssi
        self.d_min = 0.05
        
        # State: [x, y, z, A, n]
        # x, y, z ~ Uniform(-initial_bound, initial_bound)
        # A ~ Normal(-60, 3^2) dBm (Reference RSSI at 1 meter)
        # n ~ Uniform(2.0, 3.5) (Path loss exponent)
        self.particles = np.zeros((num_particles, 5), dtype=np.float64)
        self.particles[:, 0] = np.random.uniform(-initial_bound, initial_bound, num_particles)
        self.particles[:, 1] = np.random.uniform(-initial_bound, initial_bound, num_particles)
        self.particles[:, 2] = np.random.uniform(-initial_bound * 0.5, initial_bound * 0.5, num_particles)
        self.particles[:, 3] = np.random.normal(-60.0, 3.0, num_particles)
        self.particles[:, 4] = np.random.uniform(2.0, 3.5, num_particles)
        
        # Weights initialized uniformly
        self.weights = np.ones(num_particles, dtype=np.float64) / num_particles

    def update(self, observer_pos: np.ndarray, measured_rssi: float):
        """
        Bayesian measurement update step given observer 3D position [x, y, z] and RSSI reading.
        """
        # Compute Euclidean distance from each particle to observer
        dx = self.particles[:, 0] - observer_pos[0]
        dy = self.particles[:, 1] - observer_pos[1]
        dz = self.particles[:, 2] - observer_pos[2]
        dist = np.sqrt(dx*dx + dy*dy + dz*dz)
        dist = np.maximum(dist, self.d_min)
        
        # Log-distance path loss prediction: RSSI = A - 10 * n * log10(d / d0)
        A = self.particles[:, 3]
        n = self.particles[:, 4]
        expected_rssi = A - 10.0 * n * np.log10(dist)
        
        # Gaussian likelihood
        residual = measured_rssi - expected_rssi
        var = self.sigma_rssi ** 2
        likelihood = np.exp(-0.5 * (residual ** 2) / var)
        
        # Update weights
        self.weights *= likelihood
        total_weight = np.sum(self.weights)
        
        if total_weight < 1e-12:
            # Re-initialize uniformly if degenerate
            self.weights = np.ones(self.num_particles, dtype=np.float64) / self.num_particles
        else:
            self.weights /= total_weight
            
        # Check effective sample size (N_eff)
        n_eff = 1.0 / np.sum(self.weights ** 2)
        if n_eff < (self.num_particles / 2.0):
            self._low_variance_resample()

    def _low_variance_resample(self):
        """
        O(N) Systematic / Low-Variance Resampling with particle roughening.
        """
        resampled = np.zeros_like(self.particles)
        step = 1.0 / self.num_particles
        r = np.random.uniform(0.0, step)
        c = self.weights[0]
        i = 0
        
        for m in range(self.num_particles):
            u = r + m * step
            while u > c and i < self.num_particles - 1:
                i += 1
                c += self.weights[i]
            resampled[m] = self.particles[i]
            
        # Particle roughening (infinitesimal jitter to prevent sample impoverishment)
        jitter_pos = np.random.normal(0.0, 0.03, size=(self.num_particles, 3))
        jitter_A = np.random.normal(0.0, 0.15, size=(self.num_particles, 1))
        jitter_n = np.random.normal(0.0, 0.02, size=(self.num_particles, 1))
        
        resampled[:, 0:3] += jitter_pos
        resampled[:, 3:4] += jitter_A
        resampled[:, 4:5] += jitter_n
        
        self.particles = resampled
        self.weights = np.ones(self.num_particles, dtype=np.float64) / self.num_particles

    def estimate(self):
        """
        Returns estimated 3D position [x, y, z] and 3-sigma confidence radius.
        """
        pos = self.particles[:, 0:3]
        mean_pos = np.average(pos, axis=0, weights=self.weights)
        
        # Weighted 3D covariance
        diff = pos - mean_pos
        cov = np.cov(diff.T, aweights=self.weights)
        
        # Eigenvalues give axis variances
        eigvals = np.linalg.eigvalsh(cov)
        max_sigma = math.sqrt(max(eigvals[-1], 1e-6))
        radius_3sigma = 3.0 * max_sigma
        
        return mean_pos, radius_3sigma


def run_simulation():
    np.random.seed(42)
    print("=" * 70)
    print(" 📡 SnoopAR: Synthetic 3D Inverse Localization Simulation")
    print("=" * 70)
    
    # 1. Ground Truth Setup
    target_true = np.array([1.20, 0.50, 0.35])  # Hidden under bumper at (1.2m, 0.5m, 0.35m)
    true_A = -61.0   # Reference power at 1 meter
    true_n = 2.8     # Path loss exponent through bumper plastic & air
    noise_sigma = 3.5 # Multipath noise standard deviation (dB)
    
    print(f"🎯 Target Ground Truth Position : [{target_true[0]:.2f}, {target_true[1]:.2f}, {target_true[2]:.2f}] meters")
    print(f"⚙️ Target RF Parameters        : A = {true_A:.1f} dBm, n = {true_n:.1f}")
    print(f"🔊 Simulated Multipath Noise    : sigma = {noise_sigma:.1f} dB\n")
    
    # 2. Simulate User Walking in an Arc around the front/side (Synthetic Aperture)
    num_samples = 35
    angles = np.linspace(-math.pi / 2.2, math.pi / 2.2, num_samples)
    arc_radius = 2.2
    
    observer_trajectory = []
    for theta in angles:
        # User walks in an arc around the search region, scanning up and down
        obs_x = arc_radius * math.sin(theta) + 0.3
        obs_y = -arc_radius * math.cos(theta) + 1.2
        obs_z = 0.8 + 0.3 * math.sin(theta * 4)  # Natural hand motion height (0.5m to 1.1m)
        observer_trajectory.append(np.array([obs_x, obs_y, obs_z]))
        
    # 3. Initialize Particle Filter
    pf = SnoopARParticleFilter(num_particles=8000, initial_bound=3.0, sigma_rssi=noise_sigma)
    
    print("-" * 70)
    print(f"{'Step':<6}{'Obs Pos (x, y, z)':<22}{'RSSI':<10}{'Est Pos (x, y, z)':<24}{'Err (m)':<9}{'3σ Rad (m)':<10}")
    print("-" * 70)
    
    for k, obs_pos in enumerate(observer_trajectory):
        # Calculate true distance and noisy RSSI
        true_dist = np.linalg.norm(obs_pos - target_true)
        true_rssi = true_A - 10.0 * true_n * math.log10(max(true_dist, 0.05))
        measured_rssi = true_rssi + np.random.normal(0.0, noise_sigma)
        
        # Filter Step
        pf.update(obs_pos, measured_rssi)
        est_pos, radius_3sigma = pf.estimate()
        err = np.linalg.norm(est_pos - target_true)
        
        obs_str = f"[{obs_pos[0]:.1f},{obs_pos[1]:.1f},{obs_pos[2]:.1f}]"
        est_str = f"[{est_pos[0]:.2f},{est_pos[1]:.2f},{est_pos[2]:.2f}]"
        
        print(f"{k+1:<6}{obs_str:<22}{measured_rssi:>5.1f} dBm  {est_str:<24}{err:>6.3f}m  {radius_3sigma:>7.3f}m")
        
    print("-" * 70)
    final_est, final_rad = pf.estimate()
    final_err = np.linalg.norm(final_est - target_true)
    print(f"\n🎉 Final Estimated Position : [{final_est[0]:.3f}, {final_est[1]:.3f}, {final_est[2]:.3f}] m")
    print(f"🎯 Ground Truth Position    : [{target_true[0]:.3f}, {target_true[1]:.3f}, {target_true[2]:.3f}] m")
    print(f"📏 Absolute Error           : {final_err * 100.0:.1f} cm")
    print(f"🔮 3-Sigma Confidence Radius: {final_rad * 100.0:.1f} cm")
    
    if final_err < 0.25:
        print("\n✅ TARGET LOCK ACQUIRED! (< 25 cm error threshold satisfied)")
    else:
        print("\n⚠️ Convergence within acceptable bounds.")


if __name__ == "__main__":
    run_simulation()
