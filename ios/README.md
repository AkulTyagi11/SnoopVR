# SnoopAR iOS Reference Application

This directory contains the native **iOS reference implementation** for **SnoopAR**, integrating the shared C++20 core engine with Apple ARKit, CoreBluetooth, and SceneKit / SwiftUI.

---

## Architecture Overview

```
ios/
└── SnoopAR/
    ├── SnoopAR-Bridging-Header.h      # C-ABI bridge to core/include/snoopar/snoopar_core_c.h
    ├── Engine/
    │   └── SnoopArEngineWrapper.swift # Swift wrapper managing the SnoopAREngineHandle
    ├── AR/
    │   └── ArKitController.swift      # ARKit 6-DoF VIO, Y-up coordinates, pose interpolation
    ├── BLE/
    │   └── TrackerScanner.swift       # CoreBluetooth low-latency scanner & protocol classifier
    ├── Rendering/
    │   └── VoxelSceneRenderer.swift   # 3D SceneKit rendering for breadcrumbs & voxel clouds
    └── UI/
        └── ContentView.swift          # SwiftUI HUD, telemetry card, and suspect tracker sheet
```

---

## Key Features

1. **Native C++20 Core Interop:** Zero-copy C-ABI interop calling `snoopar_add_observation`, `snoopar_get_estimate`, and `snoopar_get_voxel_cloud`.
2. **Standardized ARKit Y-Up Coordinates:** Directly consumes ARKit camera transforms matching ARCore coordinate conventions (gravity along $-Y$, floor plane spanned by $(X, Z)$).
3. **CoreBluetooth Packet Sniffing:**
   - Scans with `CBCentralManagerScanOptionAllowDuplicatesKey: true` for continuous RSSI streams.
   - Strict protocol filtering:
     - Apple Find My (`0x004c`, type `0x12`, $\ge 25$ bytes payload).
     - Samsung SmartThings Find (`0xFD5A`).
     - Tile (`0xFEED` / `0xFEEC`).
     - Google Find My Device (`0xFEAA` with frame type `0x40`/`0x41`).
4. **SceneKit 3D Holographic Rendering:** Renders floating signal breadcrumbs and glowing thermal volumetric voxel clouds directly over the live camera feed.
5. **Directional Haptics:** Triggers `UIImpactFeedbackGenerator` pulses upon target lock.

---

## Required `Info.plist` Permissions

Ensure the following privacy usage descriptions are included in your Xcode target:

```xml
<key>NSCameraUsageDescription</key>
<string>SnoopAR requires camera access for Visual-Inertial Odometry motion tracking.</string>

<key>NSBluetoothAlwaysUsageDescription</key>
<string>SnoopAR requires Bluetooth access to detect and localize nearby BLE tracking tags.</string>

<key>NSBluetoothPeripheralUsageDescription</key>
<string>SnoopAR requires Bluetooth access to detect and localize nearby BLE tracking tags.</string>
```

---

## Building in Xcode

1. Open Xcode and select **Create a new iOS App** (Interface: **SwiftUI**, Language: **Swift**).
2. Set the target bundle identifier to `org.snoopar.ios`.
3. Add `ios/SnoopAR/` source files to your Xcode project target.
4. Add the shared C++ core source files to the project compile sources:
   - `core/src/rf_model.cpp`
   - `core/src/bayesian_grid.cpp`
   - `core/src/trajectory_analyzer.cpp`
   - `core/src/engine.cpp`
   - `core/src/snoopar_core_c.cpp`
5. In **Build Settings**:
   - Set **Objective-C Bridging Header** to `ios/SnoopAR/SnoopAR-Bridging-Header.h`.
   - Set **Header Search Paths** to `$(PROJECT_DIR)/../core/include`.
   - Set **C++ Language Dialect** to `C++20` (`-std=c++20`).
6. Connect a physical iPhone running iOS 16.0+ (ARKit requires physical hardware) and tap **Run**.
