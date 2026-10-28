# SnoopAR: Algorithm Specification

This document provides the mathematical formulation of the **Hierarchical Bayesian Occupancy Grid Engine**, the RF propagation model with body attenuation, analytical transmit-power marginalization, heavy-tailed Student-t likelihood evaluation, and trajectory collinearity detection.

---

## 1. Problem Formulation & Coordinate Frame

The objective is to estimate the stationary 3D coordinates of an unknown target RF emitter $\mathbf{r}_t = [x_t, y_t, z_t]^T \in \mathbb{R}^3$ given a time-series of noisy scalar RSSI measurements $z_k$ captured at synchronized 3D observer positions $\mathbf{p}_k = [x_k, y_k, z_k]^T$.

### 1.1 Standardized Coordinate System (ARCore Y-Up)
To ensure zero-copy compatibility between mobile AR frameworks (ARCore, Sceneview) and the native C++ core, SnoopAR standardizes on the **right-handed Y-up metric frame**:
* **$X$-axis:** Horizontal lateral axis (Right is $+X$, Left is $-X$).
* **$Y$-axis:** Vertical elevation axis (Up is $+Y$, Down is $-Y$, gravity acts along $-Y$).
* **$Z$-axis:** Horizontal depth axis (Backward is $+Z$, Forward is $-Z$).
* **Floor Plane:** Spanned by the horizontal coordinates $(X, Z)$.
* **Height / Elevation:** Defined strictly along the $Y$ coordinate.

---

## 2. RF Propagation & Measurement Model

### 2.1 Euclidean Metric Distance
At observation step $k \in \{1, \dots, K\}$, the Euclidean distance between observer camera position $\mathbf{p}_k$ and candidate voxel coordinate $\mathbf{r} = [x, y, z]^T$ is:

$$d_k(\mathbf{r}) = \max\left( \sqrt{(x_k - x)^2 + (y_k - y)^2 + (z_k - z)^2}, \,\, d_{\min} \right)$$

Where $d_{\min} = 0.05\text{ m}$ prevents logarithmic singularity in the immediate near field.

### 2.2 Log-Distance Path Loss with Directional Body Attenuation
The expected RSSI $\hat{z}_k(\mathbf{r})$ at candidate position $\mathbf{r}$ is modeled by:

$$\hat{z}_k(\mathbf{r}) = A - 10 \cdot n \cdot \log_{10}\left( \frac{d_k(\mathbf{r})}{d_0} \right) - L_{\text{body}}(\mathbf{p}_k, \mathbf{r}, \mathbf{u}_k)$$

Where:
* $d_0 = 1.0\text{ m}$ is the reference calibration distance.
* $A$ is the reference transmit power at $1.0\text{ m}$ (typically $-58.0\text{ dBm}$ to $-62.0\text{ dBm}$ for commercial BLE tags).
* $n$ is the effective path-loss exponent ($n \approx 2.0$ for free-space, $2.5 - 3.2$ for indoor furnished rooms and vehicle environments).
* $\mathbf{u}_k$ is the unit vector representing the phone camera optical gaze direction.
* **Body-Blocking Loss ($L_{\text{body}}$):** Human tissue attenuates 2.4 GHz RF signals by 6–12 dB. If the vector from the observer to the candidate $(\mathbf{r} - \mathbf{p}_k)$ points behind the phone's camera direction, the user's torso obstructs the direct line of sight:

$$\cos \psi = \mathbf{u}_k \cdot \frac{\mathbf{r} - \mathbf{p}_k}{\|\mathbf{r} - \mathbf{p}_k\|}$$

$$L_{\text{body}} = \begin{cases} 
L_{\max} \cdot \left(\frac{1 - \cos \psi}{2}\right)^2 & \text{if } \cos \psi < 0 \\ 
0 & \text{otherwise} 
\end{cases} \quad (L_{\max} = 8.0 \text{ dB})$$

### 2.3 Closed-Form Analytical Marginalization of Reference Transmit Power ($A$)
Simultaneously estimating both transmit power $A$ and distance $d$ produces a fundamental parameter confounding trade-off: a filter can explain a weak signal either by placing the tag far away with high power, or close by with low power.

Because $A$ enters linearly in the log-distance equation, we treat $A$ under a Gaussian conjugate prior:

$$A \sim \mathcal{N}(\mu_A, \sigma_A^2)$$

For each candidate voxel $\mathbf{r}$, define the geometric distance-and-body loss component:

$$g_k(\mathbf{r}) = -10 \cdot n \cdot \log_{10}(d_k(\mathbf{r})) - L_{\text{body}, k}(\mathbf{r})$$

Given $K$ observations $\{(\mathbf{p}_k, \mathbf{u}_k, z_k)\}_{k=1}^K$ and measurement noise variance $\sigma_{\text{rssi}}^2$:

1. **Posterior Precision:**
   $$\sigma_{A, \text{post}}^{-2} = \sigma_A^{-2} + \frac{K}{\sigma_{\text{rssi}}^2}$$

2. **Conditional Optimal Estimate $\hat{A}(\mathbf{r})$:**
   $$\hat{A}(\mathbf{r}) = \sigma_{A, \text{post}}^2 \left[ \frac{\mu_A}{\sigma_A^2} + \frac{1}{\sigma_{\text{rssi}}^2} \sum_{k=1}^K (z_k - g_k(\mathbf{r})) \right]$$

This integrates $A$ out analytically in $\mathcal{O}(K)$ time for any candidate voxel, collapsing the parameter space and eliminating the distance-power ambiguity.

### 2.4 Heavy-Tailed Likelihood (Student-t Distribution)
Standard Gaussian likelihood functions aggressively discard valid candidate positions when subjected to multipath interference, which regularly induces 10–20 dB signal dips. SnoopAR utilizes a **Student-t distribution** with $\nu = 3.0$ degrees of freedom:

$$e_k(\mathbf{r}) = z_k - \left[ \hat{A}(\mathbf{r}) + g_k(\mathbf{r}) \right]$$

$$p(z_k \mid \mathbf{r}) = \frac{\Gamma\left(\frac{\nu+1}{2}\right)}{\sqrt{\pi \nu \sigma_{\text{rssi}}^2} \,\, \Gamma\left(\frac{\nu}{2}\right)} \left( 1 + \frac{e_k^2(\mathbf{r})}{\nu \sigma_{\text{rssi}}^2} \right)^{-\frac{\nu+1}{2}}$$

In log-space:

$$\ln p(z_k \mid \mathbf{r}) = C - \frac{\nu + 1}{2} \ln\left( 1 + \frac{e_k^2(\mathbf{r})}{\nu \sigma_{\text{rssi}}^2} \right)$$

---

## 3. Hierarchical Bayesian Occupancy Voxel Grid

Rather than relying on non-deterministic Monte Carlo particle swarms that suffer from sample impoverishment and random seed divergence, SnoopAR implements a **two-stage deterministic Bayesian voxel grid**.

```
+-------------------------------------------------------------+
| Stage 1: Coarse Volumetric Grid                             |
| Resolution: 0.25 m                                          |
| Extents:    2.5 m (X) x 2.0 m (Y) x 2.5 m (Z)               |
| Voxels:     10 x 8 x 10 = 800 candidate cells               |
| Centered:   On initial observer position p_0                |
+-------------------------------------------------------------+
                              |
                     MAP Centroid Selected
                              v
+-------------------------------------------------------------+
| Stage 2: Fine Surface Sub-Grid                              |
| Resolution: 0.06 m                                          |
| Extents:    0.6 m (X) x 0.6 m (Y) x 0.6 m (Z)               |
| Voxels:     10 x 10 x 10 = 1,000 candidate cells            |
| Centered:   On coarse peak estimate r_coarse                |
+-------------------------------------------------------------+
```

### 3.1 Posterior Probability Density Computation
For any grid $\mathcal{G}$ of voxels $\{\mathbf{r}_v\}_{v=1}^V$, the unnormalized log-posterior is evaluated across all accumulated observations:

$$\ln P(\mathbf{r}_v \mid \mathcal{Z}_{1:K}) = \sum_{k=1}^K \ln p(z_k \mid \mathbf{r}_v)$$

To prevent floating-point underflow:
$$m = \max_{v} \ln P(\mathbf{r}_v \mid \mathcal{Z}_{1:K})$$
$$w_v = \exp\left( \ln P(\mathbf{r}_v \mid \mathcal{Z}_{1:K}) - m \right)$$
$$\tilde{w}_v = \frac{w_v}{\sum_{j=1}^V w_j}$$

### 3.2 Target Centroid and Confidence Radius
The estimated target position $\hat{\mathbf{r}}_t$ is computed as the expected centroid:

$$\hat{\mathbf{r}}_t = \sum_{v=1}^V \tilde{w}_v \mathbf{r}_v$$

The confidence radius $R_{90}$ is computed from the spatial covariance trace:

$$\mathbf{P}_t = \sum_{v=1}^V \tilde{w}_v (\mathbf{r}_v - \hat{\mathbf{r}}_t)(\mathbf{r}_v - \hat{\mathbf{r}}_t)^T$$
$$R_{90} = 2.0 \cdot \sqrt{\frac{1}{3} \text{Tr}(\mathbf{P}_t)}$$

---

## 4. Near-Field Path Loss Physics

At distances $d > 1.5\text{ m}$, the derivative of RSSI with respect to distance is small:

$$\left|\frac{\partial \text{RSSI}}{\partial d}\right| = \frac{10 \cdot n}{\ln(10) \cdot d} \approx \frac{10 \cdot 2.7}{2.302 \cdot 2.0} \approx 5.86 \text{ dB/m}$$

Multipath noise ($\sigma_{\text{rssi}} \approx 6.0 - 7.5\text{ dB}$) completely swamps this small gradient, preventing high-resolution localization at distance.

However, in the near field at $d = 0.35\text{ m}$:

$$\left|\frac{\partial \text{RSSI}}{\partial d}\right| \approx \frac{27.0}{2.302 \cdot 0.35} \approx 33.5 \text{ dB/m}$$

A displacement of only $10\text{ cm}$ produces a $> 3.3\text{ dB}$ signal shift. SnoopAR exploits this physical phenomenon:
1. **Stage 1** uses the weak distant gradient to locate the general quadrant within $\sim 0.8\text{ m}$.
2. **Stage 2** guides the user to perform a close surface sweep ($30 - 50\text{ cm}$), where the steep gradient collapses the confidence radius to $< 25\text{ cm}$.

---

## 5. Trajectory Geometry & Collinearity Detection

When an observer moves in a straight line, the path-loss equations possess rotational symmetry about that axis, creating an ambiguous ring of candidate positions.

### 5.1 Horizontal Floor Plane Analysis (ARCore Y-Up)
In the ARCore coordinate frame, walking trajectory occurs on the horizontal plane spanned by $(X, Z)$.
Let $\mathbf{p}_k = [x_k, y_k, z_k]^T$ for $k \in \{1, \dots, K\}$.
The mean horizontal coordinate is $[\bar{x}, \bar{z}]^T$.

The $2 \times 2$ horizontal covariance matrix is:

$$\mathbf{C}_{\text{horiz}} = \frac{1}{K} \sum_{k=1}^K \begin{bmatrix} (x_k - \bar{x})^2 & (x_k - \bar{x})(z_k - \bar{z}) \\ (x_k - \bar{x})(z_k - \bar{z}) & (z_k - \bar{z})^2 \end{bmatrix}$$

Let $\lambda_1 \ge \lambda_2 \ge 0$ be the eigenvalues of $\mathbf{C}_{\text{horiz}}$:
* **Planar Ratio:** $\rho_{\text{planar}} = \frac{\lambda_2}{\lambda_1}$.
* If $\rho_{\text{planar}} < 0.12$ and $K \ge 8$, the horizontal trajectory is collinear.
* **Guidance Prompt:** `"Step sideways to break collinear symmetry"`.

### 5.2 Vertical Elevation Span Analysis
Because users naturally hold their phones at a constant chest height, vertical height estimation suffers from lack of baseline.
The elevation span is monitored along the $Y$-axis:

$$\Delta Y = \max_{k} y_k - \min_{k} y_k$$

* If $\Delta Y < 0.25\text{ m}$ after horizontal convergence, the system prompts:
* **Guidance Prompt:** `"Scan higher or lower to constrain height"`.

---

## 6. Algorithmic Complexity

| Operation | Complexity | Memory Allocation | Mobile Execution Time (ARM Cortex-A78) |
| :--- | :--- | :--- | :--- |
| Closed-Form $A$ Update | $\mathcal{O}(K)$ | Zero (registers) | $< 0.5\,\mu\text{s}$ per voxel |
| Coarse Grid Likelihood ($800$ cells) | $\mathcal{O}(V_{\text{coarse}} \cdot K)$ | Zero (pre-allocated) | $\approx 0.12\text{ ms}$ ($K = 35$) |
| Fine Grid Likelihood ($1,000$ cells) | $\mathcal{O}(V_{\text{fine}} \cdot K)$ | Zero (pre-allocated) | $\approx 0.15\text{ ms}$ ($K = 35$) |
| Trajectory Covariance Eigendecomposition | $\mathcal{O}(K)$ | Zero (stack $2 \times 2$) | $< 1.0\,\mu\text{s}$ |
| **Total Engine Cycle per Observation** | $\mathcal{O}(V \cdot K)$ | **0.00 MB** | **$< 0.35\text{ ms}$** |
