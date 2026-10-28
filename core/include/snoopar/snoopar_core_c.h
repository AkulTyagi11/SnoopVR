#pragma once

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* SnoopAREngineHandle;

typedef struct {
    float x;
    float y;
    float z;
    float probability; // Normalized [0.0, 1.0]
} SnoopARVoxel;

// Lifecycle
SnoopAREngineHandle snoopar_create_engine(int32_t tracker_type);
void snoopar_destroy_engine(SnoopAREngineHandle handle);
void snoopar_reset_engine(SnoopAREngineHandle handle);
void snoopar_set_tracker_type(SnoopAREngineHandle handle, int32_t tracker_type);

// Input Observations
void snoopar_add_observation(
    SnoopAREngineHandle handle,
    float obs_x, float obs_y, float obs_z,
    float gaze_x, float gaze_y, float gaze_z,
    float rssi,
    uint64_t timestamp_ns
);

// State & Guidance
int32_t snoopar_get_search_state(SnoopAREngineHandle handle);
int32_t snoopar_get_guidance_hint(SnoopAREngineHandle handle);
void snoopar_start_close_sweep(SnoopAREngineHandle handle);
size_t snoopar_get_observation_count(SnoopAREngineHandle handle);

// Spatial Outputs
int32_t snoopar_get_estimate(
    SnoopAREngineHandle handle,
    float* out_x, float* out_y, float* out_z,
    float* out_confidence_radius
);

int32_t snoopar_get_bearing_to_target(
    SnoopAREngineHandle handle,
    float cam_x, float cam_y, float cam_z,
    float gaze_x, float gaze_y, float gaze_z,
    float* out_azimuth_deg,
    float* out_elevation_deg,
    float* out_distance_m
);

size_t snoopar_get_voxel_cloud(
    SnoopAREngineHandle handle,
    SnoopARVoxel* out_voxels,
    size_t max_count
);

#ifdef __cplusplus
}
#endif
