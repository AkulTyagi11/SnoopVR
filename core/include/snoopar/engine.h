#pragma once

#include "snoopar/types.h"
#include "snoopar/bayesian_grid.h"
#include "snoopar/trajectory_analyzer.h"
#include <memory>
#include <mutex>
#include <vector>

namespace snoopar {

class Engine {
public:
    Engine(TrackerType type = TrackerType::AirTag);

    void reset();
    void set_tracker_type(TrackerType type);
    TrackerProfile get_tracker_profile() const { return profile_; }

    void add_observation(const Vec3& observer_pos, const Vec3& camera_gaze, float rssi, uint64_t timestamp_ns = 0);

    bool get_estimate(Vec3& out_position, float& out_confidence_radius) const;
    void get_voxel_cloud(std::vector<Voxel>& out_voxels, size_t max_count = 200) const;

    // Computes relative horizontal azimuth (degrees [-180, 180]), elevation (degrees), and distance (meters) to target
    bool get_bearing_to_target(
        const Vec3& current_cam_pos,
        const Vec3& current_cam_gaze,
        float& out_azimuth_deg,
        float& out_elevation_deg,
        float& out_distance_m
    ) const;

    SearchState get_search_state() const { return state_; }
    GuidanceHint get_guidance_hint() const;

    // Transitions from coarse stage to guided close sweep around the current estimate
    void start_close_sweep();

    // Surface plane prior constraint (e.g. detected car bumper, wall, or floor)
    void set_surface_plane(const Vec3& plane_point, const Vec3& plane_normal, float sigma_meters = 0.08f);
    void clear_surface_plane();
    bool has_surface_plane() const { return has_plane_constraint_; }

    size_t get_observation_count() const { return observations_.size(); }

private:
    mutable std::recursive_mutex mutex_;
    TrackerProfile profile_;
    SearchState state_{SearchState::CoarseSearch};

    bool has_plane_constraint_{false};
    Vec3 plane_point_{0.0f, 0.0f, 0.0f};
    Vec3 plane_normal_{0.0f, 1.0f, 0.0f};
    float plane_sigma_{0.08f};

    std::vector<BleObservation> observations_;
    BayesianGrid coarse_grid_;
    std::unique_ptr<BayesianGrid> fine_grid_;
    TrajectoryAnalyzer trajectory_analyzer_;
};

} // namespace snoopar
