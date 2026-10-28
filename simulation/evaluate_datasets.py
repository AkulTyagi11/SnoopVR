"""
Automated Replay & Benchmark Evaluator for SnoopAR Datasets.
Tests the compiled C++20 Core Engine against simulated and empirical session files in simulation/data/.
Reports Stage 1 Coarse error, Stage 2 Fine error, and convergence behavior.
"""

import json
import math
import sys
from pathlib import Path

# Ensure root core python wrapper is importable
repo_root = Path(__file__).parent.parent
sys.path.insert(0, str(repo_root))

from core.python.snoopar_core import SnoopAREngine


def evaluate_session(dataset_path: Path):
    with open(dataset_path, "r", encoding="utf-8") as f:
        session = json.load(f)
        
    meta = session["metadata"]
    gt = meta["ground_truth"]
    gt_pos = (gt["x"], gt["y"], gt["z"])
    tracker_type_str = meta.get("tracker_type", "Apple_FindMy_Tag")
    
    if "SmartTag" in tracker_type_str:
        tracker_type = SnoopAREngine.TRACKER_SMARTTAG
    elif "Tile" in tracker_type_str:
        tracker_type = SnoopAREngine.TRACKER_TILE
    else:
        tracker_type = SnoopAREngine.TRACKER_AIRTAG
        
    engine = SnoopAREngine(tracker_type=tracker_type)
    observations = session["observations"]
    
    print("=" * 80)
    print(f" Evaluating Session: {meta['session_id']}")
    print(f" Environment:        {meta['environment']}")
    print(f" Tracker Profile:    {tracker_type_str}")
    print(f" Ground Truth:       [{gt_pos[0]:.2f}, {gt_pos[1]:.2f}, {gt_pos[2]:.2f}]")
    print(f" True RF Model:      A={meta['true_rf_params']['true_A_dBm']} dBm, n={meta['true_rf_params']['true_n']}")
    print("-" * 80)
    
    stage1_obs = [o for o in observations if o["stage"] == "COARSE_SWEEP"]
    stage2_obs = [o for o in observations if o["stage"] == "CLOSE_SWEEP"]
    
    # 1. Replay Stage 1 (Coarse Perimeter Sweep)
    for obs in stage1_obs:
        p = obs["camera_pos"]
        g = obs["camera_gaze"]
        engine.add_observation(
            obs_x=p[0], obs_y=p[1], obs_z=p[2],
            gaze_x=g[0], gaze_y=g[1], gaze_z=g[2],
            rssi=obs["rssi"],
            timestamp_ns=obs["timestamp_ns"]
        )
        
    coarse_res = engine.get_estimate()
    if not coarse_res:
        print("[FAIL] Coarse stage produced no estimate!")
        return False
        
    coarse_pos, coarse_rad = coarse_res
    coarse_err = math.sqrt(
        (coarse_pos[0] - gt_pos[0])**2 +
        (coarse_pos[1] - gt_pos[1])**2 +
        (coarse_pos[2] - gt_pos[2])**2
    )
    print(f" [STAGE 1 COARSE] {len(stage1_obs)} observations:")
    print(f"  Estimate:   [{coarse_pos[0]:.3f}, {coarse_pos[1]:.3f}, {coarse_pos[2]:.3f}] (Error: {coarse_err*100:.1f} cm)")
    print(f"  Uncertainty Radius: {coarse_rad*100:.1f} cm")
    print(f"  Search State:       {engine.get_search_state()}")
    
    # 2. Trigger Close Sweep
    engine.start_close_sweep()
    print("\n [TRANSITION] Triggered start_close_sweep() -> High-res subgrid initialized")
    
    # 3. Replay Stage 2 (Close Surface Sweep)
    for obs in stage2_obs:
        p = obs["camera_pos"]
        g = obs["camera_gaze"]
        engine.add_observation(
            obs_x=p[0], obs_y=p[1], obs_z=p[2],
            gaze_x=g[0], gaze_y=g[1], gaze_z=g[2],
            rssi=obs["rssi"],
            timestamp_ns=obs["timestamp_ns"]
        )
        
    fine_res = engine.get_estimate()
    if not fine_res:
        print("[FAIL] Fine stage produced no estimate!")
        return False
        
    fine_pos, fine_rad = fine_res
    fine_err = math.sqrt(
        (fine_pos[0] - gt_pos[0])**2 +
        (fine_pos[1] - gt_pos[1])**2 +
        (fine_pos[2] - gt_pos[2])**2
    )
    
    # 4. Test Bearing from observer position
    last_obs = stage2_obs[-1]
    lp = last_obs["camera_pos"]
    lg = last_obs["camera_gaze"]
    bearing = engine.get_bearing_to_target(
        cam_x=lp[0], cam_y=lp[1], cam_z=lp[2],
        gaze_x=lg[0], gaze_y=lg[1], gaze_z=lg[2]
    )
    
    print(f"\n [STAGE 2 CLOSE SWEEP] {len(observations)} total observations:")
    print(f"  Final Estimate:     [{fine_pos[0]:.3f}, {fine_pos[1]:.3f}, {fine_pos[2]:.3f}] (Error: {fine_err*100:.1f} cm)")
    print(f"  Uncertainty Radius: {fine_rad*100:.1f} cm")
    if bearing:
        print(f"  Directional Bearing: Azimuth: {bearing[0]:.1f}°, Elevation: {bearing[1]:.1f}°, Distance: {bearing[2]:.2f}m")
        
    voxels = engine.get_voxel_cloud(max_count=50)
    print(f"  AR Volumetric Cloud: {len(voxels)} probability voxels extracted")
    
    success = fine_err < 0.35
    status_str = "[PASS] Physical Target Pinpointed (< 35 cm)!" if success else "[FAIL] Error exceeds 35 cm threshold"
    print(f"  Result:             {status_str}")
    print("=" * 80 + "\n")
    return success


def main():
    data_dir = Path(__file__).parent / "data"
    if not data_dir.exists():
        print(f"No data directory found at {data_dir}")
        return 1
        
    files = list(data_dir.glob("*.json"))
    if not files:
        print("No .json benchmark datasets found.")
        return 1
        
    all_passed = True
    for f in sorted(files):
        passed = evaluate_session(f)
        if not passed:
            all_passed = False
            
    if all_passed:
        print(">> ALL PRE-RECORDED BENCHMARK SESSIONS PASSED! <<")
        return 0
    else:
        print(">> SOME BENCHMARK SESSIONS FAILED! <<")
        return 1


if __name__ == "__main__":
    sys.exit(main())
