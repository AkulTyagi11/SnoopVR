# 🗺️ SnoopAR Development Roadmap

This roadmap tracks the planned milestones for **SnoopAR**, progressing from mathematical verification to production mobile releases.

---

## 📍 Phase 1: Mathematical Foundations & Synthetic Simulator
> **Goal:** Validate the 5D Bayesian Particle Filter against simulated multipath noise before touching mobile hardware.

- [ ] **1.1 Core Math Engine in Rust (`snoopar-core`):**
  - Implement the 5D state representation $[x_t, y_t, z_t, A, n]$.
  - Implement Log-Distance Path Loss likelihood evaluation.
  - Implement $\mathcal{O}(N)$ Low-Variance Resampling and particle jittering.
- [ ] **1.2 Python / Rust Synthetic Test Harness:**
  - Simulate a static emitter at $(1.5, 0.2, 0.8)\text{ m}$.
  - Simulate an observer moving in an arc with Rayleigh multipath noise ($\sigma = 4.5\text{ dB}$).
  - Benchmark convergence rate ($< 25$ samples to achieve $< 25\text{ cm}$ error).
- [ ] **1.3 Collinearity Detection:**
  - Implement SVD-based trajectory collinearity analyzer to warn against straight-line sweeps.

---

## 📱 Phase 2: Mobile Sensor Ingestion & Temporal Sync
> **Goal:** Capture high-fidelity synchronized sensor streams on Android and iOS devices.

- [ ] **2.1 Android BLE Ingestion:**
  - Build `BluetoothLeScanner` foreground service with `SCAN_MODE_LOW_LATENCY`.
  - Parse Apple Find My, Samsung SmartTag, Tile, and Google Find My Device payloads.
- [ ] **2.2 iOS CoreBluetooth Ingestion:**
  - Build `CBCentralManager` scanner with duplicate packet delivery enabled.
  - Fingerprint tracking tags using manufacturer and service data keys.
- [ ] **2.3 AR Visual-Inertial Odometry Pipeline:**
  - Initialize ARCore (Android) and ARKit (iOS) sessions.
  - Extract 6-DoF camera poses $[x, y, z, q_w, q_x, q_y, q_z]$ at 60 Hz.
- [ ] **2.4 Microsecond Temporal Synchronizer:**
  - Build circular pose buffer with linear/spherical interpolation for exact BLE arrival timestamp matching.

---

## 🔮 Phase 3: AR Visualization & Live Localization
> **Goal:** Connect the math engine to real-time 3D camera holograms.

- [ ] **3.1 C-FFI / JNI Bindings:**
  - Bind `snoopar-core` into Kotlin (JNI) and Swift (C-Interop).
- [ ] **3.2 Trajectory Breadcrumb Rendering:**
  - Draw 3D spatial points floating in camera space along the user's walked path, colored by RSSI.
- [ ] **3.3 Holographic Target Halo:**
  - Render an animated, pulsing volumetric 3D sphere anchored at the estimated target centroid.
  - Dynamically scale sphere radii to match the $3\sigma$ covariance ellipsoid.
- [ ] **3.4 Directional Haptic & Audio Geiger:**
  - Implement audio/haptic pulse frequency modulated by the dot product of the camera gaze vector and target direction.

---

## 🛡️ Phase 4: Real-World Vehicle & Field Testing
> **Goal:** Test against real AirTags and trackers concealed in difficult vehicular environments.

- [ ] **4.1 Automotive Stress Tests:**
  - Test tags hidden in rear bumper cavities, wheel wells, gloveboxes, and under seats.
  - Calibrate path-loss parameters ($A$ and $n$) across automotive steel vs plastic bumpers.
- [ ] **4.2 LiDAR Occlusion Integration (iOS Pro):**
  - Use ARKit scene depth / mesh anchors so the AR beacon appears occluded behind physical bumper panels rather than rendering on top.
- [ ] **4.3 "Silent Tag" Verification:**
  - Verify detection of physical AirTags with disconnected speaker hardware.

---

## 🚀 Phase 5: v1.0 Public Open-Source Release
> **Goal:** Public release, documentation, and community distribution.

- [ ] F-Droid and Google Play release (Android).
- [ ] TestFlight and App Store release (iOS).
- [ ] Complete developer documentation and contribution guides.
- [ ] Publish pre-recorded synthetic benchmark datasets for research citation.
