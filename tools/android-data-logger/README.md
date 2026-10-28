# 📱 SnoopAR Android Data Logger

> **Phase 0 Empirical Ground-Truth Capture Tool**  
> *Record synchronized 60 Hz ARCore 6-DoF camera poses and BLE advertising packets to build real-world RF test datasets.*

---

## 🎯 Purpose

As demonstrated in our mathematical review, simulating RF multipath with simple Gaussian noise does not capture the true complexity of real-world reflections around automotive steel bodies, ground bounces, and user body attenuation.

This Android app allows developers and researchers to:
1. Tape a real tracking tag (AirTag, SmartTag, Tile) at a measured spot on a car or in a room.
2. Walk an arbitrary search trajectory while the phone logs **exact 6-DoF camera poses** and **raw BLE packet bursts**.
3. Replay and evaluate the recorded sweep offline using the SnoopAR C++20 Core engine.

---

## 📂 Output File Formats

Each recorded session is saved in `Documents/SnoopAR_Sessions/session_<TIMESTAMP>/`:

### 1. `poses.csv` (60 Hz Visual-Inertial Odometry)
```csv
timestamp_ns,pos_x,pos_y,pos_z,qw,qx,qy,qz,tracking_state
1730000000000,0.012,-0.045,0.850,0.999,0.012,-0.005,0.021,TRACKING
```

### 2. `ble.csv` (Low-Latency Promiscuous Sniffing)
```csv
timestamp_ns,device_id,tag_type,rssi,raw_hex
1730000000500,FF:12:34:56:78:9A,Apple_FindMy_AirTag,-64,02010612ff4c001219...
```

### 3. `metadata.json`
```json
{
  "session_id": "session_20261028_123000",
  "device_model": "Google Pixel 8 Pro",
  "android_version": "14",
  "notes": "Rear bumper sweep, AirTag taped 30cm behind right reflector"
}
```

---

## 🚀 How to Build & Install

1. Open the `tools/android-data-logger` directory in **Android Studio** (Giraffe or newer).
2. Connect your ARCore-supported Android device via USB with USB Debugging enabled.
3. Click **Run 'app'** (`Shift + F10`) to compile and launch.

---

## 🧪 How to Record a Ground-Truth Dataset

1. Place your AirTag / SmartTag at a measured location (e.g., mark $(x=1.2\text{ m}, y=0.4\text{ m}, z=0.3\text{ m})$ relative to your starting position).
2. Open the app and grant Camera and Bluetooth permissions.
3. Tap **"Start Empirical Sweep"**.
4. Walk an arc around the car at $1.5 - 2\text{ m}$ distance (Coarse Stage).
5. Move closer and sweep the phone within $30 - 50\text{ cm}$ along the suspect bumper panel (Close Sweep Stage).
6. Tap **"Stop & Save CSVs"**.
7. Tap **"Export / Share"** or pull the files via ADB:
   ```bash
   adb pull /sdcard/Documents/SnoopAR_Sessions/ ./my_recording/
   ```

---

## 🔁 Offline Replay & Verification

Run the Python replay script in `scripts/`:

```bash
python scripts/replay_session.py ./my_recording/session_XXXX --ground-truth 1.20 0.40 0.30
```

The script will automatically interpolate camera poses at exact BLE arrival times, feed them into `SnoopAREngine` via C++ FFI, and report the real-world centimeter accuracy!
