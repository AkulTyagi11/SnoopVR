# Contributing to SnoopAR

Thank you for your interest in contributing to **SnoopAR**! 

SnoopAR is an open-source, community-led privacy project designed to level the playing field against covert surveillance and unwanted tracking. We welcome contributions from developers, researchers, signal-processing engineers, and privacy advocates.

---

## High-Priority Areas for Contribution

We have broken down active development into four primary tracks:

### Track 1: Core Localization & RF Modeling (C++20)
* **Task 1.1:** Extend the 3D Bayesian Occupancy Grid in `core/` to evaluate multi-hypothesis path-loss exponents ($n$).
* **Task 1.2:** Enhance the trajectory analyzer with principal component analysis (PCA) for non-planar sweep diagnostics.
* **Task 1.3:** Maintain zero external dependencies, standard C-ABI interop, and verify cross-platform builds via CMake.

### Track 2: Android Reference App (Kotlin + Jetpack Compose + ARCore)
* **Task 2.1:** Optimize custom OpenGL ES 2.0 point-sprite shaders for rendering 3D volumetric thermal clouds in `ArGlRenderer`.
* **Task 2.2:** Add automated sensor drift warnings and relocalization cues when ARCore reports limited tracking.
* **Task 2.3:** Enhance Geiger haptics and audio feedback based on camera gaze alignment.

### Track 3: iOS Reference App (Swift + SwiftUI + ARKit)
* **Task 3.1:** Connect the native C++ core (`snoopar_core_c.h`) via Swift C-interop.
* **Task 3.2:** Build the `CoreBluetooth` scanner service for Apple Find My, Samsung SmartTag, and Tile profiles.
* **Task 3.3:** Implement the ARKit session tracking loop and RealityKit / Metal volumetric rendering.

### Track 4: Real-World Empirical Datasets & Tag Signatures
* **Task 4.1:** Record real-world sessions using the Data Logger (`tools/android-data-logger`) with tags placed in vehicles (bumpers, wheel wells, gloveboxes) and rooms.
* **Task 4.2:** Replay sessions via `scripts/replay_session.py` to calibrate path-loss parameters across metal chassis versus plastic bumpers.
* **Task 4.3:** Expand BLE fingerprinting in `TrackerScanner` for new commercial and custom tracker firmware.

---

## Development Workflow

1. **Fork the Repository:** Create your feature branch:
   ```bash
   git checkout -b feature/close-sweep-optimization
   ```
2. **Follow Code Quality Guidelines:**
   * **C++:** Adhere to modern standard C++20. Run `clang-format -style=file` on `core/include/` and `core/src/`. Maintain zero external runtime dependencies.
   * **Kotlin:** Follow official Android Kotlin style guides and ensure thread safety on GLSurfaceView and background Coroutine contexts.
   * **Swift:** Follow idiomatic Swift style guidelines and SwiftUI declarative patterns.
3. **Run Unit Tests:**
   * Build and run the C++ core test suite:
     ```bash
     cmake -B core/build -S core
     cmake --build core/build
     ./core/build/snoopar_test
     ```
   * Run the Monte Carlo simulation benchmark:
     ```bash
     python simulation/benchmark_snoopar.py
     ```
4. **Submit a Pull Request:** Provide a clear description of the changes, any screenshots/videos of AR rendering, and references to open issues.

---

## Responsible Security & Privacy Policy

SnoopAR is strictly an **anti-surveillance and defensive security tool**. 
* We do **not** accept contributions that facilitate unauthorized tracking, jamming, spoofing, or weaponization of RF signals.
* SnoopAR does **not** collect telemetry, phone home, or transmit any user location or sensor data over the network.

---

## Community & Discussions

* **GitHub Issues:** For bug reports, feature proposals, and algorithm debates.
* **Architecture Discussions:** See [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) and [ALGORITHM_SPECIFICATION.md](ALGORITHM_SPECIFICATION.md).
