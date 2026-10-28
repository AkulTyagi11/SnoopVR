# SnoopAR: Development Roadmap

This roadmap tracks the development milestones for **SnoopAR**, progressing from mathematical verification and shared engine implementation to mobile platform integration and public open-source releases.

---

## Phase 0: Empirical Data Capture & Hardware Verification
> **Status: Complete**  
> **Goal:** Build a production Android tool to capture real-world synchronized ARCore poses and BLE RSSI packets with ground-truth coordinates.

- [x] **0.1 Android Data Logger (`tools/android-data-logger`):**
  - Continuous `GLSurfaceView.Renderer` camera session running at real ARCore rate (~30 Hz).
  - AR plane hit-testing to pin physical ground-truth tag coordinates on tap.
  - Low-latency BLE scanner capturing hardware arrival timestamps (`result.timestampNanos`).
  - Thread-safe CSV session writer with periodic buffer flushing and structured `metadata.json` export.
- [x] **0.2 Replay & Evaluation Tooling:**
  - Python session replay script (`scripts/replay_session.py`) with microsecond pose interpolation.
  - Filters out uninitialized / non-`TRACKING` frames and evaluates tracking error against ground truth.

---

## Phase 1: Shared Core Engine & Mathematical Verification
> **Status: Complete**  
> **Goal:** Implement the portable zero-dependency core localization engine and benchmark against multipath fading.

- [x] **1.1 C++20 Shared Core (`core/`):**
  - Standardized ARCore Y-up metric coordinates ($Y$ is elevation, $(X, Z)$ is the horizontal floor plane).
  - Closed-form analytical marginalization of reference transmit power $A$ via 1D Gaussian conjugate updating.
  - Heavy-tailed Student-t likelihood evaluation ($\nu = 3.0$) accommodating 10–20 dB multipath dips.
  - Real-time torso body-blocking model ($L_{\text{body}}$ based on camera gaze vector).
  - Deterministic Hierarchical Bayesian Occupancy Voxel Grid (Coarse 800 voxels $\to$ Fine 1,000 voxels).
  - Trajectory analyzer with horizontal floor-plane collinearity ($2\times 2$ covariance matrix) and vertical elevation span monitoring.
  - Pure C-ABI (`snoopar_core_c.h`) for zero-overhead cross-platform interop.
- [x] **1.2 Rigorous Test Suite & Benchmarks:**
  - Unit test suite (`core/tests/test_engine.cpp`) with 6/6 tests passing.
  - 30-seed Monte Carlo simulation benchmark (`simulation/benchmark_snoopar.py`) under Rayleigh fading and body blocking (97% of runs within 50 cm, median error 32 cm).
  - Python `ctypes` wrapper (`core/python/snoopar_core.py`) with automated Windows MinGW runtime resolution.

---

## Phase 2: Android Native Application
> **Status: Complete**  
> **Goal:** Build the native Android AR application integrating the C++ core via JNI, camera background rendering, and interactive UI.

- [x] **2.1 Native JNI Integration:**
  - JNI bindings (`snoopar_jni.cpp`) mapping `SnoopArNativeEngine` to `libsnoopar_core`.
  - CMake build script configured with multi-level directory resolution to shared root core.
- [x] **2.2 High-Fidelity ARCore & OpenGL ES Rendering:**
  - `BackgroundRenderer`: Fullscreen quad sampling `GL_TEXTURE_EXTERNAL_OES` with display geometry transformations.
  - `BreadcrumbRenderer`: 3D signal history points colored by RSSI strength with synchronized vertex buffer snapshotting.
  - `VoxelCloudRenderer`: Thermal glowing volumetric probability cloud with additive alpha blending and thread-safe buffer swapping.
- [x] **2.3 BLE Packet Scanning & Strict Protocol Classification:**
  - Filter and fingerprint Apple Find My (`0x004c`, type `0x12`, $\ge 25$ bytes).
  - Filter Samsung SmartThings Find (`0xFD5A` service data).
  - Filter Google Find My Device Network (`0xFEAA` with frame type `0x40`/`0x41`).
  - Filter Tile trackers (`0xFEED`/`0xFEEC`/`0x01D3`).
- [x] **2.4 User Interface & Guidance:**
  - Jetpack Compose HUD overlay showing search stage, guidance hints, sample counts, and confidence radius.
  - Suspect tracker discovery sheet with device selection and target lock-on.
  - Directional Geiger haptic feedback driven by camera-to-target alignment.

---

## Phase 3: Empirical Validation & Vehicle Calibration
> **Status: In Progress**  
> **Goal:** Collect empirical vehicle datasets using the Data Logger and calibrate environmental path-loss parameters.

- [ ] **3.1 Vehicle Exterior & Interior Datasets:**
  - Record datasets for AirTags and SmartTags concealed in vehicle bumpers, wheel wells, and glove compartments.
  - Measure empirical path-loss exponent $n$ through plastic bumpers vs steel frames.
- [ ] **3.2 Model Parameter Tuning:**
  - Refine prior parameters $(\mu_A, \sigma_A, n)$ in `types.h` based on real capture replays.
  - Verify close-sweep convergence on real hardware in diverse indoor and outdoor settings.

---

## Phase 4: iOS Native Application
> **Status: Planned**  
> **Goal:** Implement the native iOS application leveraging ARKit, RealityKit/Metal, and CoreBluetooth.

- [ ] **4.1 Swift C-Interop:**
  - Integrate `snoopar_core_c.h` into an Xcode project via module map.
- [ ] **4.2 ARKit VIO Tracking:**
  - Capture 60 Hz `ARFrame` camera poses and microsecond timestamp synchronization.
- [ ] **4.3 CoreBluetooth Scanner:**
  - Background and foreground BLE scanning with manufacturer data extraction.
- [ ] **4.4 RealityKit / Metal Rendering:**
  - Render volumetric point clouds and trajectory breadcrumbs in ARKit scene space.

---

## Phase 5: Open-Source Release & Packaging
> **Status: Planned**  
> **Goal:** Distribute SnoopAR to the privacy and security community.

- [ ] Publish standalone signed Android APK releases on GitHub.
- [ ] Submit to F-Droid repository.
- [ ] Public documentation site with video demonstrations and vehicle search guides.
- [ ] Publish benchmark datasets and evaluation scripts for academic and privacy research.
