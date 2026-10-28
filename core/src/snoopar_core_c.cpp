#include "snoopar/snoopar_core_c.h"
#include "snoopar/engine.h"
#include <vector>

using namespace snoopar;

extern "C" {

SnoopAREngineHandle snoopar_create_engine(int32_t tracker_type) {
    auto type = static_cast<TrackerType>(tracker_type);
    return new Engine(type);
}

void snoopar_destroy_engine(SnoopAREngineHandle handle) {
    if (handle) {
        delete static_cast<Engine*>(handle);
    }
}

void snoopar_reset_engine(SnoopAREngineHandle handle) {
    if (handle) {
        static_cast<Engine*>(handle)->reset();
    }
}

void snoopar_set_tracker_type(SnoopAREngineHandle handle, int32_t tracker_type) {
    if (handle) {
        static_cast<Engine*>(handle)->set_tracker_type(static_cast<TrackerType>(tracker_type));
    }
}

void snoopar_add_observation(
    SnoopAREngineHandle handle,
    float obs_x, float obs_y, float obs_z,
    float gaze_x, float gaze_y, float gaze_z,
    float rssi,
    uint64_t timestamp_ns
) {
    if (handle) {
        static_cast<Engine*>(handle)->add_observation(
            Vec3(obs_x, obs_y, obs_z),
            Vec3(gaze_x, gaze_y, gaze_z),
            rssi,
            timestamp_ns
        );
    }
}

int32_t snoopar_get_search_state(SnoopAREngineHandle handle) {
    if (handle) {
        return static_cast<int32_t>(static_cast<Engine*>(handle)->get_search_state());
    }
    return 0;
}

int32_t snoopar_get_guidance_hint(SnoopAREngineHandle handle) {
    if (handle) {
        return static_cast<int32_t>(static_cast<Engine*>(handle)->get_guidance_hint());
    }
    return 0;
}

void snoopar_start_close_sweep(SnoopAREngineHandle handle) {
    if (handle) {
        static_cast<Engine*>(handle)->start_close_sweep();
    }
}

size_t snoopar_get_observation_count(SnoopAREngineHandle handle) {
    if (handle) {
        return static_cast<Engine*>(handle)->get_observation_count();
    }
    return 0;
}

int32_t snoopar_get_estimate(
    SnoopAREngineHandle handle,
    float* out_x, float* out_y, float* out_z,
    float* out_confidence_radius
) {
    if (!handle || !out_x || !out_y || !out_z || !out_confidence_radius) {
        return 0;
    }
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
}

size_t snoopar_get_voxel_cloud(
    SnoopAREngineHandle handle,
    SnoopARVoxel* out_voxels,
    size_t max_count
) {
    if (!handle || !out_voxels || max_count == 0) {
        return 0;
    }
    std::vector<Voxel> voxels;
    static_cast<Engine*>(handle)->get_voxel_cloud(voxels, max_count);
    for (size_t i = 0; i < voxels.size(); ++i) {
        out_voxels[i].x = voxels[i].x;
        out_voxels[i].y = voxels[i].y;
        out_voxels[i].z = voxels[i].z;
        out_voxels[i].probability = voxels[i].probability;
    }
    return voxels.size();
}

} // extern "C"
