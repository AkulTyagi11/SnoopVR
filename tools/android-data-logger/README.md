# SnoopAR Android Data Logger

> **Phase 0 Empirical Ground-Truth Capture Tool**  
> *Record synchronized ARCore 6-DoF camera poses and BLE advertising packets to build real-world RF test datasets.*

---

## Purpose

Simulating RF multipath with simple Gaussian noise does not capture the true complexity of real-world reflections around automotive steel bodies, ground bounces, and user body attenuation.

This Android app allows developers and researchers to:
1. Place a real tracking tag (AirTag, SmartTag, Tile) at a measured spot on a car or in a room.
2. Tap the tag on screen to record its exact 3D ground-truth position in the ARCore world frame.
3. Walk an empirical search trajectory while the phone logs **exact 6-DoF camera poses** and **raw BLE packet bursts**.
4. Replay and evaluate the recorded sweep offline using the SnoopAR C++20 Core engine.

---

## Output File Formats

Each recorded session is saved in `Android/data/org.snoopar.logger/files/Documents/SnoopAR_Sessions/session_<TIMESTAMP>/`:

### 1. `poses.csv` (Visual-Inertial Odometry)
```csv
timestamp_ns,pos_x,pos_y,pos_z,qw,qx,qy,qz,tracking_state
1730000000000,0.012,-0.045,0.850,0.999,0.012,-0.005,0.021,TRACKING
```

### 2. `ble.csv` (Low-Latency Promiscuous Sniffing)
```csv
timestamp_ns,device_id,tag_type,rssi,raw_hex
1730000000500,FF:12:34:56:78:9A,Apple_FindMy_Tag,-64,02010612ff4c001219...
```

### 3. `metadata.json`
```json
{
  "session_dir": "session_20261028_123000",
  "start_time": "Wed Oct 28 12:30:00 GMT 2026",
  "device_model": "Google Pixel 8 Pro",
  "android_version": "14",
  "notes": "Sweep session with ground truth",
  "ground_truth_position": [1.10, 0.40, 0.30]
}
```

---

## How to Build & Install

1. Open `tools/android-data-logger` in **Android Studio** (Giraffe or newer).
2. Connect your ARCore-supported Android device via USB with USB Debugging enabled.
3. Click **Run 'app'** (`Shift + F10`) or run:
   ```bash
   ./gradlew assembleDebug
   ```

---

## How to Record a Ground-Truth Dataset

1. Place your AirTag / SmartTag at the test location (e.g. on a car bumper).
2. Launch the app and grant Camera and Bluetooth permissions.
3. **Tap on the tag in the camera view:** ARCore hit-tests the surface and pins the ground-truth coordinate in `metadata.json`.
4. Tap **"Start Empirical Sweep"**.
5. Walk an arc around the car at $1.5 - 2\text{ m}$ distance (Coarse Stage).
6. Move closer and sweep the phone within $30 - 50\text{ cm}$ along the suspect surface (Close Sweep Stage).
7. Tap **"Stop & Save CSVs"**.
8. Pull the recording to your computer:
   ```bash
   adb pull /sdcard/Android/data/org.snoopar.logger/files/Documents/SnoopAR_Sessions/ ./my_recordings/
   ```

---

## Offline Replay & Verification

Run the Python replay script in `scripts/`:

```bash
python scripts/replay_session.py ./my_recordings/session_XXXX
```

The script will automatically load the ground-truth position from `metadata.json`, interpolate camera poses at exact BLE arrival times, feed them into `SnoopAREngine` via C++ FFI, and report the real-world centimeter accuracy.
