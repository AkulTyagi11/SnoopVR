# 📡 SnoopAR / SnoopVR

> **Augmented Reality Spatial Pinpointer for Hidden Trackers & Covert Surveillance Devices**  
> *Turn invisible 2.4 GHz radio waves into a 3D glowing volumetric lock in your phone's camera view.*

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Core: C++20](https://img.shields.io/badge/Core-C%2B%2B20_Zero--Dependency-00599C.svg)](core/)
[![Validation: Monte_Carlo_30_Seeds](https://img.shields.io/badge/Benchmark-97%25_%3C50cm-brightgreen.svg)](simulation/benchmark_snoopar.py)
[![Community: Open Source](https://img.shields.io/badge/Contributions-Welcome-brightgreen.svg)](docs/CONTRIBUTING.md)

---

## 🔍 The Problem SnoopAR Solves

Bluetooth trackers (Apple AirTags, Samsung Galaxy SmartTags, Google Find My tags, Tile, and custom micro-beacons) have created an unprecedented stalking and vehicle theft crisis. While iOS and Android now display basic notifications (*"Unknown tracker traveling with you"*), **actually finding where the tag is physically hidden is agonizingly difficult**:

1. **"Silent" Stalker Trackers:** Stalkers routinely disable or drill out speaker diaphragms. "Silent AirTags" are widely traded on eBay and Etsy. The official "Play Sound" button does nothing.
2. **Vague RSSI Numbers:** Third-party BLE scanners only show erratic numbers (`-85 dBm`, `-72 dBm`, `-94 dBm`). Because of 2.4 GHz multipath interference ($\lambda \approx 12.3\text{ cm}$), standing waves cause 15–20 dB signal drops across just 6 cm of movement. An ordinary person cannot locate a coin-sized tag tucked behind a car bumper using bouncing numbers.
3. **No UWB for Targets:** Apple's "Precision Finding" only works for the *registered owner* of an AirTag using their paired U1/U2 chip. Non-owners being stalked are completely locked out of directional UWB finding.

---

## 💡 The Solution: Synthetic Aperture Inverse Localization

**SnoopAR** fuses **Visual-Inertial Odometry (VIO / ARKit / ARCore)** from commodity smartphone cameras with **low-latency Bluetooth Low Energy (BLE) packet sniffing** to solve the inverse localization problem.

As you walk holding your phone:
1. **AR Motion Tracking** records the millimeter-accurate 6-DoF trajectory of your phone's camera in 3D space.
2. **BLE Sniffing** captures periodic advertisement frames and RSSI bursts from the suspicious tracker.
3. A **3D Bayesian Occupancy Grid with analytically marginalized transmit power ($A$)** eliminates impossible locations and cancels multipath nulls across the walked baseline.
4. **Augmented Reality** projects a real-time **glowing volumetric heat cloud** directly over the physical spot where the tracker is hidden.

```
       [ Suspicious BLE Tracker ]
            (Hidden in Bumper)
                   *  *  *  (2.4 GHz RF Waves)
                 *         *
   [ Phone at P1 ] ---> [ Phone at P2 ] ---> [ Phone at P3 ]
  (AR Pose + RSSI)     (AR Pose + RSSI)     (AR Pose + RSSI)
            \                  |                  /
             \                 |                 /
              v                v                v
       +-----------------------------------------------+
       |       Stage 1: Coarse Perimeter Sweep         |
       |  (1.5–2.5m Arc isolates quadrant within ~0.8m)|
       +-----------------------------------------------+
                               |
                               v
       +-----------------------------------------------+
       |       Stage 2: Guided Surface Close Sweep     |
       |  (30–50cm near-field sweep steepens gradient) |
       +-----------------------------------------------+
                               |
                               v
            [ 3D Volumetric Probability Cloud Rendered ]
                 "Target Lock: 12.5 cm inside bumper"
```

---

## 🚀 Key Features

* 🎯 **Coarse-to-Fine 3D Search:** 
  * *Stage 1 (Coarse Sector):* A standard 1.5–2.5m walk around the vehicle/room isolates the target quadrant within ~0.8m.
  * *Stage 2 (Guided Close Sweep):* In-camera visual AR prompts guide the user to sweep within 30–50 cm of the suspect surface, where steep near-field path-loss gradients overcome multipath noise to achieve a 15–25 cm physical lock.
* ☁️ **Volumetric Probability Heat Cloud:** Renders a multi-modal probability density field directly in AR. Rather than forcing an artificial center point (which can land in empty space when signals exhibit ring-shaped or symmetric ambiguity), SnoopAR shows true probability hotspots.
* 🛰️ **Multi-Protocol Classification:** Auto-detects and fingerprints:
  * **Apple Find My** (AirTag, AirTag 2nd gen, third-party Find My tags via Company ID `0x004C` Type `0x12`)
  * **Samsung SmartThings Find** (Galaxy SmartTag / SmartTag2 via Service UUID `0xFD5A` / `0x0075`)
  * **Google Find My Device Network** (FMDN beacon frames via Service UUID `0xFE2C` / `0xFEAA`)
  * **Tile** (Standard / Pro / Mate via Service UUID `0xFEED` / `0x01D3`)
  * **DULT (Detecting Unwanted Location Trackers):** Interoperable Apple/Google standard
  * **Unknown Periodic Beacons:** Custom nRF52/ESP32 stalking emitters
* 📍 **Heatmap Breadcrumbs:** Leaves a physical trail of color-coded signal nodes floating in AR along your walked trajectory.
* 🔇 **Directional Geiger Guidance:** Audio ticks and haptic feedback that accelerate when pointing the camera toward the highest probability cluster.
* 🛡️ **100% On-Device & Privacy-Preserving:** No cloud servers, no account required, completely offline.

---

## 🔬 Benchmark Proof (30-Seed Monte Carlo under Realistic Fading)

Evaluated under **Rayleigh fast fading (10–20 dB nulls), user body blocking (8 dB torso loss), and Gaussian shadowing**:

| Method / Strategy | Search Range | Median Error | Within 25 cm | Within 50 cm |
| :--- | :--- | :--- | :--- | :--- |
| **A. Naive 5D Particle Filter** | Distant Arc ($1.5 - 2.5\text{ m}$) | $102.9\text{ cm}$ | $0\%$ | $0\%$ |
| **B. Bayesian Grid with Prior & Student-t** | Distant Arc ($1.5 - 2.5\text{ m}$) | $83.4\text{ cm}$ | $3\%$ | $7\%$ |
| **C. SnoopAR Coarse-to-Fine Pipeline** | Arc $\to$ Guided Close Sweep ($30\text{ cm}$) | **$32.0\text{ cm}$** | **$20\%$** | **$97\%$** |

Run the benchmark yourself:
```bash
python simulation/benchmark_snoopar.py
```

---

## ⚡ Quickstart

### 1. Run the Python Simulation & Benchmark
```bash
# 30-seed realistic Monte Carlo benchmark
python simulation/benchmark_snoopar.py

# Interactive single-run simulator
python simulation/simulate_snoopar.py
```

### 2. Build & Test the Shared C++20 Core
```bash
# Configure with CMake
cmake -B core/build -S core

# Compile static library, shared DLL, and test suite
cmake --build core/build

# Run verification suite (6/6 PASS)
./core/build/snoopar_test
```

### 3. Use via Python FFI
```python
from core.python.snoopar_core import SnoopAREngine

engine = SnoopAREngine(SnoopAREngine.TRACKER_AIRTAG)
engine.add_observation(obs_x=1.2, obs_y=0.0, obs_z=0.8, gaze_x=0.0, gaze_y=1.0, gaze_z=0.0, rssi=-62.0)
pos, confidence_rad = engine.get_estimate()
print(f"Target estimate: {pos}, Radius: {confidence_rad}m")
```

---

## 📂 Repository Structure

```
SnoopAR/
├── core/                               # Shared C++20 Engine (Zero external dependencies)
│   ├── CMakeLists.txt                  # Builds libsnoopar_core.a, .dll, and test runner
│   ├── include/snoopar/                # Types, RF model, Bayesian grid, trajectory analyzer, C-ABI
│   ├── src/                            # High-performance C++20 implementation
│   ├── tests/test_engine.cpp           # C++ test runner (12.5 cm lock in simulation)
│   └── python/snoopar_core.py          # Python ctypes wrapper
├── tools/android-data-logger/          # Phase 0 Empirical Data Capture App
│   ├── app/                            # Android Jetpack Compose + ARCore + BLE capture app
│   └── scripts/replay_session.py       # Offline session replay & evaluation
├── simulation/
│   ├── benchmark_snoopar.py            # 30-seed Monte Carlo Rayleigh fading benchmark
│   └── simulate_snoopar.py             # Single-run simulator
├── docs/
│   ├── PROJECT_REVIEW.md               # Claude Opus 5.5 architectural critique & resolutions
│   ├── AR_RENDERING_GUIDE.md           # Android Sceneview & iOS RealityKit blueprints
│   ├── FEASIBILITY_ANALYSIS.md         # 2.4 GHz RF physics, multipath, and OS sandboxing
│   ├── SYSTEM_ARCHITECTURE.md          # Dataflow diagrams & thread isolation
│   ├── ALGORITHM_SPECIFICATION.md      # Exact mathematical formulas & Student-t likelihood
│   ├── CONTRIBUTING.md                 # Open-source contribution guidelines & tracks
│   └── ROADMAP.md                      # Milestones from Phase 1 to Phase 5
└── LICENSE                             # Apache 2.0 License
```

---

## ⚠️ Scope & Prior Art

* **What SnoopAR Locates:** Short-range 2.4 GHz RF tracking tags (Bluetooth Low Energy).
* **What is Out of Scope:** Dedicated **Cellular / GPS trackers** (magnetic 2G/4G vehicle trackers). These do not emit Bluetooth and cannot be detected via RF sniffing without dedicated cellular spectrum analyzers.
* **Relationship to Prior Art:**
  * Projects like [AirGuard](https://arxiv.org/abs/2202.11813) (TU Darmstadt) and native Android/iOS alerts excel at detecting *presence* over time and triggering the DULT sound alarm.
  * **SnoopAR's unique niche** begins where they stop: when an alert is fired, but the tag is **silent** (speaker physically removed or disabled) or non-standard, and the user must physically locate a coin-sized object concealed inside complex vehicle bodywork or furniture.

---

## 🤝 Open Source & Contributing

SnoopAR is designed from day one to be an **open-source, community-driven tool** against surveillance and unwanted tracking. Whether you are an **AR developer**, an **RF/DSP engineer**, a **mobile UI designer**, or an **anti-stalking advocate**, your help is welcome!

Please read [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) to see how you can get involved.

---

## ⚖️ License

Distributed under the Apache 2.0 License. See `LICENSE` for details.
