# 📐 SnoopAR: Algorithm Specification

This document provides the complete mathematical formulation of the **Inverse Localization Particle Filter**, the RF propagation model, likelihood evaluation, resampling strategies, and geometric dilution of precision (GDOP) detection.

---

## 1. Problem Formulation

The objective is to estimate the stationary 3D coordinates of an unknown target RF emitter $\mathbf{r}_t = [x_t, y_t, z_t]^T \in \mathbb{R}^3$ given a time-series of noisy scalar RSSI measurements $z_k$ taken at known 3D observer positions $\mathbf{p}_k = [x_k, y_k, z_k]^T$.

### 1.1 State Vector
To account for unknown transmission powers and local environmental clutter, the state vector $\mathbf{\theta}$ is defined in $\mathbb{R}^5$:

$$\mathbf{\theta} = \begin{bmatrix} x_t \\ y_t \\ z_t \\ A \\ n \end{bmatrix}$$

Where:
* $[x_t, y_t, z_t]$: 3D position coordinates of the emitter in the AR metric world frame (meters).
* $A$: Calibrated reference RSSI at distance $d_0 = 1.0\text{ m}$ (typically $-58 \text{ dBm}$ to $-64 \text{ dBm}$ for BLE).
* $n$: Effective path-loss exponent ($n \approx 2.0$ for open air, $2.5 - 3.5$ for vehicle interiors and furnished rooms).

---

## 2. RF Propagation & Measurement Model

### 2.1 Euclidean Metric Distance
At observation step $k$, the Euclidean distance between the current camera position $\mathbf{p}_k$ and a candidate particle $\mathbf{r}_t^{(i)}$ is:

$$d_k^{(i)} = \max\left( \sqrt{(x_k - x_t^{(i)})^2 + (y_k - y_t^{(i)})^2 + (z_k - z_t^{(i)})^2}, \,\, d_{\min} \right)$$

*(Where $d_{\min} = 0.05\text{ m}$ prevents logarithmic singularity at near-zero distance).*

### 2.2 Log-Distance Path Loss with Directional Body Attenuation
The expected RSSI $\hat{z}_k^{(i)}$ for candidate position $\mathbf{r}_t^{(i)}$ is modeled by:

$$\hat{z}_k^{(i)} = A^{(i)} - 10 \cdot n^{(i)} \cdot \log_{10}\left( \frac{d_k^{(i)}}{d_0} \right) - L_{\text{body}}(\mathbf{p}_k, \mathbf{r}_t^{(i)}, \mathbf{q}_k)$$

Where:
* **Body-Blocking Loss ($L_{\text{body}}$):** Human tissue attenuates 2.4 GHz RF by 6–12 dB. The camera's forward orientation vector $\mathbf{u}_{\text{cam}}$ (from camera quaternion $\mathbf{q}_k$) indicates the user's line-of-sight. If the vector from observer to candidate $(\mathbf{r}_t^{(i)} - \mathbf{p}_k)$ points *behind* the user's phone, the user's torso obstructs the direct path:
  $$\cos \psi = \mathbf{u}_{\text{cam}} \cdot \frac{\mathbf{r}_t^{(i)} - \mathbf{p}_k}{\|\mathbf{r}_t^{(i)} - \mathbf{p}_k\|}$$
  $$L_{\text{body}} = \begin{cases} 
  L_{\max} \cdot \left(\frac{1 - \cos \psi}{2}\right)^2 & \text{if } \cos \psi < 0 \\ 
  0 & \text{otherwise} 
  \end{cases} \quad (L_{\max} \approx 8.0 \text{ dB})$$

### 2.3 Heavy-Tailed Likelihood (Student-t Distribution)
Standard Gaussian likelihood fails in real indoor/vehicular environments because multipath nulls create asymmetric, deep 10–20 dB drops. Thin Gaussian tails cause the filter to aggressively discard valid candidate positions.
We employ a **Student-t distribution** with degrees of freedom $\nu = 3$:

$$p(z_k \mid \mathbf{\theta}_k^{(i)}) = \frac{\Gamma\left(\frac{\nu+1}{2}\right)}{\sqrt{\pi \nu \sigma^2} \,\, \Gamma\left(\frac{\nu}{2}\right)} \left( 1 + \frac{(z_k - \hat{z}_k^{(i)})^2}{\nu \sigma^2} \right)^{-\frac{\nu+1}{2}}$$

This smoothly accommodates destructive multipath drops without collapsing the hypothesis space.

### 2.4 Closed-Form Analytical Marginalization of Transmit Power $A$
Because $A$ enters linearly in the log-distance equation, estimating it simultaneously with distance creates parameter confounding.
Given an informative prior $A \sim \mathcal{N}(\mu_A, \sigma_A^2)$ for a recognized device class (e.g. AirTag $\mu_A = -58.5\text{ dBm}, \sigma_A = 1.5\text{ dB}$):
For any candidate location $\mathbf{r}_t$, let $g_k = -10 \cdot n \cdot \log_{10}(d_k) - L_{\text{body}, k}$.
The optimal conditional estimate $\hat{A}$ given all measurements $\{z_1, \dots, z_K\}$ is obtained analytically via 1D Gaussian conjugate updating:

$$\sigma_{A, \text{post}}^{-2} = \sigma_A^{-2} + \frac{K}{\sigma_{\text{rssi}}^2}$$
$$\hat{A}(\mathbf{r}_t, n) = \sigma_{A, \text{post}}^2 \left[ \frac{\mu_A}{\sigma_A^2} + \frac{1}{\sigma_{\text{rssi}}^2} \sum_{k=1}^K (z_k - g_k) \right]$$

This integrates $A$ out in closed form, reducing the search space and eliminating the fundamental distance-power ambiguity.

---

## 3. Sequential Monte Carlo (Particle Filter) Execution

A swarm of $N = 10,000$ particles is maintained. Each particle represents a joint hypothesis:
$$\mathcal{S}_k = \left\{ \left( \mathbf{\theta}_k^{(i)}, w_k^{(i)} \right) \right\}_{i=1}^N$$

### 3.1 Initialization (Step $k = 0$)
Upon initiation of the search around current phone position $\mathbf{p}_0$:
* **Spatial Coordinates:** Uniformly distributed in a 3D bounding box of side length $2R$ (default $R = 3.0\text{ m}$):
  $$x_t^{(i)} \sim \mathcal{U}(x_0 - R, x_0 + R)$$
  $$y_t^{(i)} \sim \mathcal{U}(y_0 - R, y_0 + R)$$
  $$z_t^{(i)} \sim \mathcal{U}(z_0 - R, z_0 + R)$$
* **RF Parameters:**
  $$A^{(i)} \sim \mathcal{N}(-60.0, 3.0^2) \quad [\text{dBm}]$$
  $$n^{(i)} \sim \mathcal{U}(2.0, 3.5)$$
* **Weights:** Initialized uniformly:
  $$w_0^{(i)} = \frac{1}{N}, \quad \forall i \in \{1, \dots, N\}$$

---

### 3.2 Likelihood Evaluation (Correction Step)
When a new synchronized measurement $(\mathbf{p}_k, z_k)$ is received:

1. **Calculate Innovation (Residual):**
   $$e_k^{(i)} = z_k - \hat{z}_k^{(i)}$$

2. **Evaluate Gaussian Likelihood:**
   $$p(z_k \mid \mathbf{\theta}_k^{(i)}) = \frac{1}{\sqrt{2\pi \sigma_{\text{rssi}}^2}} \exp\left( -\frac{\left(e_k^{(i)}\right)^2}{2\sigma_{\text{rssi}}^2} \right)$$

3. **Update Importance Weights:**
   $$w_k^{(i)} = w_{k-1}^{(i)} \cdot p(z_k \mid \mathbf{\theta}_k^{(i)})$$

4. **Normalize Weights:**
   $$\tilde{w}_k^{(i)} = \frac{w_k^{(i)}}{\sum_{j=1}^N w_k^{(j)}}$$

---

### 3.3 Degeneracy Check & Low-Variance Resampling

To avoid sample impoverishment, the **Effective Sample Size ($N_{\text{eff}}$)** is evaluated:

$$N_{\text{eff}} = \frac{1}{\sum_{i=1}^N \left( \tilde{w}_k^{(i)} \right)^2}$$

* If $N_{\text{eff}} < \frac{N}{2}$, perform **Low-Variance (Systematic) Resampling**:
  1. Generate a single random starting number $r \sim \mathcal{U}(0, N^{-1})$.
  2. Step through cumulative weights $c_i = \sum_{j=1}^i \tilde{w}_k^{(j)}$ with constant stride $N^{-1}$.
  3. Select matching particle indices deterministically in $\mathcal{O}(N)$ time.

### 3.4 Particle Jittering (Roughening)
After resampling, add an infinitesimal Gaussian perturbation to prevent particles from collapsing into identical discrete points:

$$\mathbf{\theta}_k^{(i)} \leftarrow \mathbf{\theta}_k^{(i)} + \mathbf{\eta}^{(i)}, \quad \mathbf{\eta}^{(i)} \sim \mathcal{N}\left( \mathbf{0}, \text{diag}(\sigma_x^2, \sigma_y^2, \sigma_z^2, \sigma_A^2, \sigma_n^2) \right)$$

*(Typical dispersion: $\sigma_{x,y,z} = 0.02\text{ m}$, $\sigma_A = 0.1\text{ dB}$, $\sigma_n = 0.02$).*

---

## 4. Centroid Extraction & 3D Covariance Ellipsoid

The AR renderer requires the estimated target center and the spatial uncertainty bounds to draw the volumetric halo.

### 4.1 Estimated Target Location (Centroid)
$$\hat{\mathbf{r}}_t = \sum_{i=1}^N \tilde{w}_k^{(i)} \begin{bmatrix} x_t^{(i)} \\ y_t^{(i)} \\ z_t^{(i)} \end{bmatrix}$$

### 4.2 3D Spatial Covariance Matrix
$$\mathbf{P}_k = \sum_{i=1}^N \tilde{w}_k^{(i)} \left( \mathbf{r}_t^{(i)} - \hat{\mathbf{r}}_t \right) \left( \mathbf{r}_t^{(i)} - \hat{\mathbf{r}}_t \right)^T \in \mathbb{R}^{3 \times 3}$$

### 4.3 Holographic Halo Radii & Orientation
Perform Eigenvalue Decomposition on $\mathbf{P}_k$:
$$\mathbf{P}_k = \mathbf{V} \mathbf{\Lambda} \mathbf{V}^T$$
* **Eigenvectors ($\mathbf{V}$):** Define the 3D rotation matrix of the AR ellipsoid.
* **Eigenvalues ($\lambda_1, \lambda_2, \lambda_3$):** The $3\sigma$ semi-axes lengths of the bounding AR halo:
  $$r_1 = 3\sqrt{\lambda_1}, \quad r_2 = 3\sqrt{\lambda_2}, \quad r_3 = 3\sqrt{\lambda_3}$$
* When $\max(r_1, r_2, r_3) < 0.25\text{ m}$, the system declares a **Target Lock** and sounds the haptic alert.

---

## 5. Geometric Dilution of Precision (GDOP) & User Guidance

If the user walks in a straight line, the path-loss equations possess axial rotational symmetry around that line, preventing unique 3D convergence.

### 5.1 Collinearity Detection
Compute the Singular Value Decomposition (SVD) of the centered observer trajectory matrix $\mathbf{X}_{\text{traj}} = [\mathbf{p}_1 - \bar{\mathbf{p}}, \dots, \mathbf{p}_k - \bar{\mathbf{p}}]^T \in \mathbb{R}^{k \times 3}$:

$$\mathbf{X}_{\text{traj}} = \mathbf{U} \mathbf{\Sigma} \mathbf{V}^T, \quad \mathbf{\Sigma} = \text{diag}(s_1, s_2, s_3)$$

* **Collinearity Metric:**
  $$\rho_{\text{planar}} = \frac{s_2}{s_1}, \quad \rho_{\text{volume}} = \frac{s_3}{s_1}$$
* If $\rho_{\text{planar}} < 0.15$ after $k \ge 8$ samples, the motion is deemed purely linear.
* **AR UI Action:** Render an animated 3D curved arrow on the floor with the instruction:  
  **"Step 1 meter sideways to break collinear symmetry."**
