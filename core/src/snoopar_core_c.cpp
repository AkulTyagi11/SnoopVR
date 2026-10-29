#include "snoopar/snoopar_core_c.h"
#include "snoopar/engine.h"
#include <vector>

using namespace snoopar;

extern "C" {

SnoopAREngineHandle snoopar_create_engine(int32_t tracker_type) {
    try {
        auto type = static_cast<TrackerType>(tracker_type);
        return new Engine(type);
    } catch (...) {
        return nullptr;
    }
}

void snoopar_destroy_engine(SnoopAREngineHandle handle) {
    if (!handle) return;
    try {
        delete static_cast<Engine*>(handle);
    } catch (...) {}
}

void snoopar_reset_engine(SnoopAREngineHandle handle) {
    if (!handle) return;
    try {
        static_cast<Engine*>(handle)->reset();
    } catch (...) {}
}

void snoopar_set_tracker_type(SnoopAREngineHandle handle, int32_t tracker_type) {
    if (!handle) return;
    try {
        static_cast<Engine*>(handle)->set_tracker_type(static_cast<TrackerType>(tracker_type));
    } catch (...) {}
}

void snoopar_add_observation(
    SnoopAREngineHandle handle,
    float obs_x, float obs_y, float obs_z,
    float gaze_x, float gaze_y, float gaze_z,
    float rssi,
    uint64_t timestamp_ns
) {
    if (!handle) return;
    try {
        static_cast<Engine*>(handle)->add_observation(
            Vec3(obs_x, obs_y, obs_z),
            Vec3(gaze_x, gaze_y, gaze_z),
            rssi,
            timestamp_ns
        );
    } catch (...) {}
}

int32_t snoopar_get_search_state(SnoopAREngineHandle handle) {
    if (!handle) return 0;
    try {
        return static_cast<int32_t>(static_cast<Engine*>(handle)->get_search_state());
    } catch (...) {
        return 0;
    }
}

int32_t snoopar_get_guidance_hint(SnoopAREngineHandle handle) {
    if (!handle) return 0;
    try {
        return static_cast<int32_t>(static_cast<Engine*>(handle)->get_guidance_hint());
    } catch (...) {
        return 0;
    }
}

void snoopar_start_close_sweep(SnoopAREngineHandle handle) {
    if (!handle) return;
    try {
        static_cast<Engine*>(handle)->start_close_sweep();
    } catch (...) {}
}

size_t snoopar_get_observation_count(SnoopAREngineHandle handle) {
    if (!handle) return 0;
    try {
        return static_cast<Engine*>(handle)->get_observation_count();
    } catch (...) {
        return 0;
    }
}

int32_t snoopar_get_estimate(
    SnoopAREngineHandle handle,
    float* out_x, float* out_y, float* out_z,
    float* out_confidence_radius
) {
    if (!handle || !out_x || !out_y || !out_z || !out_confidence_radius) {
        return 0;
    }
    try {
        Vec3 pos;
        float rad = 0.0f;
        bool ok = static_cast<Engine*>(handle)->get_estimate(pos, rad);
        if (ok) {
            *out_x = pos.x;
            *out_y = pos.y;
            *out_z = pos.z;
            *out_confidence_radius = rad;
            return 1;
        }
        return 0;
    } catch (...) {
        return 0;
    }
}

int32_t snoopar_get_bearing_to_target(
    SnoopAREngineHandle handle,
    float cam_x, float cam_y, float cam_z,
    float gaze_x, float gaze_y, float gaze_z,
    float* out_azimuth_deg,
    float* out_elevation_deg,
    float* out_distance_m
) {
    if (!handle || !out_azimuth_deg || !out_elevation_deg || !out_distance_m) {
        return 0;
    }
    try {
        Vec3 cam_pos(cam_x, cam_y, cam_z);
        Vec3 cam_gaze(gaze_x, gaze_y, gaze_z);
        bool ok = static_cast<Engine*>(handle)->get_bearing_to_target(
            cam_pos, cam_gaze,
            *out_azimuth_deg, *out_elevation_deg, *out_distance_m
        );
        return ok ? 1 : 0;
    } catch (...) {
        return 0;
    }
}

size_t snoopar_get_voxel_cloud(
    SnoopAREngineHandle handle,
    SnoopARVoxel* out_voxels,
    size_t max_count
) {
    if (!handle || !out_voxels || max_count == 0) {
        return 0;
    }
    try {
        std::vector<Voxel> voxels;
        static_cast<Engine*>(handle)->get_voxel_cloud(voxels, max_count);
        for (size_t i = 0; i < voxels.size(); ++i) {
            out_voxels[i].x = voxels[i].x;
            out_voxels[i].y = voxels[i].y;
            out_voxels[i].z = voxels[i].z;
            out_voxels[i].probability = voxels[i].probability;
        }
        return voxels.size();
    } catch (...) {
        return 0;
    }
}

void snoopar_set_surface_plane(
    SnoopAREngineHandle handle,
    float px, float py, float pz,
    float nx, float ny, float nz,
    float sigma_meters
) {
    if (!handle) return;
    try {
        static_cast<Engine*>(handle)->set_surface_plane(
            Vec3(px, py, pz),
            Vec3(nx, ny, nz),
            sigma_meters
        );
    } catch (...) {}
}

void snoopar_clear_surface_plane(SnoopAREngineHandle handle) {
    if (!handle) return;
    try {
        static_cast<Engine*>(handle)->clear_surface_plane();
    } catch (...) {}
}

} // extern "C"
