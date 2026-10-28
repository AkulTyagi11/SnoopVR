"""
Generates realistic, physically-grounded benchmark datasets for SnoopAR.
Simulates ARCore Y-up camera odometry and BLE packet captures under Rayleigh fading and torso occlusion.
Saves session files to simulation/data/.
"""

import json
import math
import random
from pathlib import Path


def generate_session(
    filename: str,
    environment_name: str,
    tracker_type: str,
    true_target: list,
    true_A: float,
    true_n: float,
    coarse_x_range: list,
    coarse_z_base: float,
    close_z_offset: float,
    seed: int = 42
):
    random.seed(seed)
    observations = []
    
    # Stage 1: Coarse Perimeter Sweep
    # User traverses across the suspected area (e.g. car rear or room couch)
    num_coarse = 20
    for i in range(num_coarse):
        t = i / (num_coarse - 1.0)
        
        # User walks across lateral X with a natural curve approaching the structure
        obs_x = coarse_x_range[0] + t * (coarse_x_range[1] - coarse_x_range[0]) + random.gauss(0, 0.02)
        obs_y = 0.85 + (i % 2) * 0.15 + random.gauss(0, 0.02)
        obs_z = coarse_z_base - 0.45 * (1.0 - (2.0 * t - 1.0) ** 2) + random.gauss(0, 0.02)
        
        # Camera aims towards suspected structure / target area
        dx = true_target[0] - obs_x
        dy = true_target[1] - obs_y
        dz = true_target[2] - obs_z
        actual_dist = max(math.sqrt(dx * dx + dy * dy + dz * dz), 0.05)
        
        gaze_x = dx / actual_dist + random.gauss(0, 0.03)
        gaze_y = dy / actual_dist + random.gauss(0, 0.02)
        gaze_z = dz / actual_dist + random.gauss(0, 0.03)
        g_len = math.sqrt(gaze_x * gaze_x + gaze_y * gaze_y + gaze_z * gaze_z)
        gaze_x /= g_len
        gaze_y /= g_len
        gaze_z /= g_len
        
        path_loss = -10.0 * true_n * math.log10(actual_dist)
        
        # Torso attenuation
        cos_psi = (gaze_x * dx + gaze_y * dy + gaze_z * dz) / actual_dist
        body_loss = 0.0
        if cos_psi < 0.0:
            body_loss = 8.0 * ((1.0 - cos_psi) / 2.0) ** 2
            
        u = random.uniform(1e-4, 1.0 - 1e-4)
        rayleigh = 10.0 * math.log10(-math.log(u)) + 2.507
        shadowing = random.gauss(0, 1.5)
        
        rssi = true_A + path_loss - body_loss + rayleigh + shadowing
        timestamp_ns = 1_000_000_000 * (i + 1)
        
        observations.append({
            "step": i + 1,
            "stage": "COARSE_SWEEP",
            "camera_pos": [round(obs_x, 4), round(obs_y, 4), round(obs_z, 4)],
            "camera_gaze": [round(gaze_x, 4), round(gaze_y, 4), round(gaze_z, 4)],
            "rssi": round(rssi, 1),
            "timestamp_ns": timestamp_ns
        })
        
    # Stage 2: Close Surface Sweep (15 points in 3-row raster grid across suspect area surface at ~30cm standoff)
    num_close = 15
    for j in range(num_close):
        t = j / (num_close - 1.0)
        sweep_x = true_target[0] - 0.35 + t * 0.70 + random.gauss(0, 0.01)
        # 3 vertical rows: lower (-18cm), middle (0cm), upper (+18cm) relative to target
        sweep_y = true_target[1] - 0.18 + (j % 3) * 0.18 + random.gauss(0, 0.01)
        sweep_z = true_target[2] + close_z_offset + random.gauss(0, 0.01)
        
        # Camera aims directly into the surface plane
        gaze_x = random.gauss(0, 0.02)
        gaze_y = random.gauss(0, 0.02)
        gaze_z = -1.0 if close_z_offset > 0 else 1.0
        g_len = math.sqrt(gaze_x * gaze_x + gaze_y * gaze_y + gaze_z * gaze_z)
        gaze_x /= g_len
        gaze_y /= g_len
        gaze_z /= g_len
        
        dx = true_target[0] - sweep_x
        dy = true_target[1] - sweep_y
        dz = true_target[2] - sweep_z
        actual_dist = max(math.sqrt(dx * dx + dy * dy + dz * dz), 0.05)
        path_loss = -10.0 * true_n * math.log10(actual_dist)
        
        u = random.uniform(1e-4, 1.0 - 1e-4)
        rayleigh = 10.0 * math.log10(-math.log(u)) + 2.507
        shadowing = random.gauss(0, 1.2)
        
        rssi = true_A + path_loss + rayleigh + shadowing
        timestamp_ns = 21_000_000_000 + 1_000_000_000 * (j + 1)
        
        observations.append({
            "step": num_coarse + j + 1,
            "stage": "CLOSE_SWEEP",
            "camera_pos": [round(sweep_x, 4), round(sweep_y, 4), round(sweep_z, 4)],
            "camera_gaze": [round(gaze_x, 4), round(gaze_y, 4), round(gaze_z, 4)],
            "rssi": round(rssi, 1),
            "timestamp_ns": timestamp_ns
        })
        
    session_data = {
        "metadata": {
            "session_id": filename.replace(".json", ""),
            "environment": environment_name,
            "tracker_type": tracker_type,
            "ground_truth": {
                "x": true_target[0],
                "y": true_target[1],
                "z": true_target[2]
            },
            "true_rf_params": {
                "true_A_dBm": true_A,
                "true_n": true_n
            },
            "total_observations": len(observations),
            "notes": "Generated with realistic 2.4 GHz Rayleigh multipath fading & torso body attenuation."
        },
        "observations": observations
    }
    
    out_dir = Path(__file__).parent / "data"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / filename
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(session_data, f, indent=2)
    print(f"Generated benchmark dataset: {out_path} ({len(observations)} observations)")


if __name__ == "__main__":
    generate_session(
        filename="vehicle_rear_bumper_airtag.json",
        environment_name="Vehicle Exterior Plastic Bumper",
        tracker_type="Apple_FindMy_Tag",
        true_target=[0.80, 0.42, -1.05],
        true_A=-63.0,
        true_n=2.85,
        coarse_x_range=[-1.0, 1.5],
        coarse_z_base=0.5,
        close_z_offset=0.30,
        seed=101
    )
    generate_session(
        filename="indoor_couch_cushion_smarttag.json",
        environment_name="Living Room Fabric Couch",
        tracker_type="Samsung_SmartTag",
        true_target=[-0.45, 0.45, 1.15],
        true_A=-62.0,
        true_n=2.65,
        coarse_x_range=[-1.5, 0.8],
        coarse_z_base=2.4,
        close_z_offset=0.32,
        seed=105
    )
