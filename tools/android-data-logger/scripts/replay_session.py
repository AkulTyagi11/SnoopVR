#!/usr/bin/env python3
"""
SnoopAR Session Replay & Offline Ground-Truth Evaluation
=======================================================
Replays recorded empirical sessions from the Android Data Logger
through the SnoopAR C++20 Core Engine via Python ctypes FFI.
"""

import sys
import argparse
from pathlib import Path
import csv
import bisect
import numpy as np

# Ensure snoopar_core can be loaded
script_dir = Path(__file__).resolve().parent
repo_root = script_dir.parents[2]
sys.path.append(str(repo_root / "core" / "python"))

try:
    from snoopar_core import SnoopAREngine
except ImportError as e:
    print(f"Error loading snoopar_core: {e}")
    print("Ensure the C++ core library has been compiled in core/build/")
    sys.exit(1)


def load_poses(poses_path):
    timestamps = []
    poses = []
    quaternions = []

    with open(poses_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            t = int(row["timestamp_ns"])
            x = float(row["pos_x"])
            y = float(row["pos_y"])
            z = float(row["pos_z"])
            qw = float(row["qw"])
            qx = float(row["qx"])
            qy = float(row["qy"])
            qz = float(row["qz"])

            timestamps.append(t)
            poses.append(np.array([x, y, z]))
            quaternions.append(np.array([qw, qx, qy, qz]))

    return timestamps, poses, quaternions


def interpolate_pose(target_t, timestamps, poses, quaternions):
    idx = bisect.bisect_left(timestamps, target_t)
    if idx == 0:
        return poses[0], quaternions[0]
    if idx >= len(timestamps):
        return poses[-1], quaternions[-1]

    t0, t1 = timestamps[idx - 1], timestamps[idx]
    if t1 == t0:
        return poses[idx], quaternions[idx]

    alpha = (target_t - t0) / (t1 - t0)
    pos = (1.0 - alpha) * poses[idx - 1] + alpha * poses[idx]
    # Simple linear interpolation for quaternion
    q = (1.0 - alpha) * quaternions[idx - 1] + alpha * quaternions[idx]
    q /= np.linalg.norm(q)
    return pos, q


def quat_to_forward(q):
    qw, qx, qy, qz = q
    # Forward optical axis (-Z in standard ARKit/ARCore camera frame)
    fx = -2.0 * (qx * qz + qw * qy)
    fy = -2.0 * (qy * qz - qw * qx)
    fz = -(1.0 - 2.0 * (qx * qx + qy * qy))
    norm = np.sqrt(fx * fx + fy * fy + fz * fz)
    if norm > 1e-6:
        return np.array([fx / norm, fy / norm, fz / norm])
    return np.array([0.0, 1.0, 0.0])


def replay_session(session_dir, target_id=None, ground_truth=None):
    session_path = Path(session_dir)
    poses_file = session_path / "poses.csv"
    ble_file = session_path / "ble.csv"

    if not poses_file.exists() or not ble_file.exists():
        print(f"Error: Session directory must contain poses.csv and ble.csv ({session_path})")
        return

    print("=" * 70)
    print(f" 📡 SnoopAR Empirical Session Replay: {session_path.name}")
    print("=" * 70)

    t_poses, poses, quats = load_poses(poses_file)
    print(f"Loaded {len(poses)} AR camera poses spanning {(t_poses[-1] - t_poses[0]) * 1e-9:.1f} seconds")

    engine = SnoopAREngine(SnoopAREngine.TRACKER_AIRTAG)

    ble_count = 0
    with open(ble_file, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            dev_id = row["device_id"]
            if target_id and target_id not in dev_id:
                continue

            tag_type = row["tag_type"]
            # Filter non-tracking background devices if not specified
            if not target_id and not (tag_type.startswith("Apple") or tag_type.startswith("Samsung") or tag_type.startswith("Tile")):
                continue

            t_ble = int(row["timestamp_ns"])
            rssi = float(row["rssi"])

            # Synchronize with AR trajectory
            pos, q = interpolate_pose(t_ble, t_poses, poses, quats)
            gaze = quat_to_forward(q)

            engine.add_observation(pos[0], pos[1], pos[2], gaze[0], gaze[1], gaze[2], rssi, t_ble)
            ble_count += 1

    print(f"Synchronized & Ingested {ble_count} target BLE packets")

    # Coarse estimate
    coarse_est, coarse_rad = engine.get_estimate()
    print(f"\nStage 1 Coarse Estimate : [{coarse_est[0]:.2f}, {coarse_est[1]:.2f}, {coarse_est[2]:.2f}] m (Radius: {coarse_rad*100:.1f} cm)")

    # Transition to close sweep evaluation
    engine.start_close_sweep()
    fine_est, fine_rad = engine.get_estimate()
    print(f"Stage 2 Refined Lock    : [{fine_est[0]:.2f}, {fine_est[1]:.2f}, {fine_est[2]:.2f}] m (Radius: {fine_rad*100:.1f} cm)")

    if ground_truth:
        gt = np.array(ground_truth, dtype=np.float64)
        error_cm = np.linalg.norm(np.array(fine_est) - gt) * 100.0
        print(f"\n🎯 Ground Truth Position: {gt.tolist()} m")
        print(f"📏 Absolute Error       : {error_cm:.1f} cm")
        if error_cm < 30.0:
            print("✅ SUCCESS: Target successfully locked within 30 cm!")
        else:
            print("⚠️ Target located within coarse quadrant.")

    # Show top probability voxels
    voxels = engine.get_voxel_cloud(max_count=5)
    print("\nTop AR Probability Voxels (x, y, z, weight):")
    for v in voxels:
        print(f"  [{v[0]:.2f}, {v[1]:.2f}, {v[2]:.2f}] -> prob: {v[3]:.2f}")


def main():
    parser = argparse.ArgumentParser(description="Replay SnoopAR empirical recordings")
    parser.add_argument("session_dir", help="Path to session folder containing poses.csv and ble.csv")
    parser.add_argument("--target", help="Filter for specific target device MAC or substring", default=None)
    parser.add_argument("--ground-truth", nargs=3, type=float, help="True (x, y, z) position in meters", default=None)
    args = parser.parse_args()

    replay_session(args.session_dir, args.target, args.ground_truth)


if __name__ == "__main__":
    main()
