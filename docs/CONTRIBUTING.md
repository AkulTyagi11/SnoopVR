# 🤝 Contributing to SnoopAR

Thank you for your interest in contributing to **SnoopAR**! 

SnoopAR is an open-source, community-led privacy project designed to level the playing field against covert surveillance and unwanted tracking. We welcome contributions from developers, researchers, mathematicians, and privacy advocates of all backgrounds.

---

## 🎯 High-Priority Areas for Contribution

We have broken down the initial implementation into four core tracks:

### Track 1: Core Localization & Math Engine (Rust / C++)
* **Task 1.1:** Implement the 5D state Particle Filter specified in [ALGORITHM_SPECIFICATION.md](ALGORITHM_SPECIFICATION.md).
* **Task 1.2:** Build a standalone synthetic benchmark harness: generate simulated 3D trajectories and noisy RSSI curves, and verify convergence rates.
* **Task 1.3:** Package the core engine as a static C-ABI library with bindings for Android (JNI) and iOS (Swift C-interop).

### Track 2: Android Reference App (Kotlin + Jetpack Compose + ARCore)
* **Task 2.1:** Implement `BluetoothLeScanner` service with high-frequency RSSI extraction and manufacturer data parsing.
* **Task 2.2:** Build the ARCore session manager and 6-DoF camera pose stream.
* **Task 2.3:** Implement the temporal sync buffer interpolating poses at microsecond BLE arrival times.
* **Task 2.4:** Render the 3D glowing volumetric halo and trajectory breadcrumb spheres using Sceneview or Filament.

### Track 3: iOS Reference App (Swift + SwiftUI + ARKit)
* **Task 3.1:** Implement `CoreBluetooth` scanner targeting Apple Find My (`0xFD6F` / `0x004c`), Tile, and SmartTag profiles.
* **Task 3.2:** Configure ARKit `ARSession` and extract camera world transform matrices.
* **Task 3.3:** Render the AR halo using RealityKit / Metal with depth-occlusion support (especially on LiDAR-equipped Pro iPhones).

### Track 4: Protocol Signatures & Real-World Dataset Collection
* **Task 4.1:** Expand the signature database in `FingerprintClassifier` for newly emerging tracking tags and smart glasses.
* **Task 4.2:** Record and contribute raw `.csv` or `.json` telemetry datasets (synchronized timestamp, camera $[x, y, z]$, and tag RSSI) from real-world vehicle and room sweeps to help benchmark the algorithm.

---

## 💻 Development Workflow

1. **Fork the Repository:** Create your own feature branch:
   ```bash
   git checkout -b feature/particle-filter-rust
   ```
2. **Follow Code Quality Guidelines:**
   * **Rust:** Run `cargo fmt` and `cargo clippy`. Ensure zero compiler warnings.
   * **Kotlin:** Follow official Android Kotlin style guides and ktlint rules.
   * **Swift:** Follow SwiftLint rules and idiomatic SwiftUI conventions.
3. **Write Unit Tests:** Any changes to signal processing, path-loss models, or byte-parsing logic must include unit tests.
4. **Submit a Pull Request:** Provide a clear description of the changes, any screenshots/videos of AR rendering, and references to open issues.

---

## 🛡️ Responsible Security & Privacy Policy

SnoopAR is strictly an **anti-surveillance and defensive security tool**. 
* We do **not** accept contributions that facilitate unauthorized tracking, jamming, spoofing, or weaponization of RF signals.
* SnoopAR does **not** collect telemetry, phone home, or transmit any user location or sensor data over the internet.

---

## 💬 Community & Discussions

* **GitHub Issues:** For bug reports, feature proposals, and algorithm debates.
* **Architecture Discussions:** See [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) and [FEASIBILITY_ANALYSIS.md](FEASIBILITY_ANALYSIS.md).

Together, let's make privacy and physical safety accessible to everyone.
