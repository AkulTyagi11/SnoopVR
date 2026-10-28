#include "snoopar/engine.h"
#include <iostream>
#include <algorithm>
#include <cmath>

namespace snoopar {

Engine::Engine(TrackerType type)
    : profile_(snoopar::get_tracker_profile(type)),
      coarse_grid_(Vec3(0.0f, 0.0f, 0.0f), Vec3(4.0f, 1.8f, 4.0f), 0.15f) {
}

void Engine::reset() {
    state_ = SearchState::CoarseSearch;
    observations_.clear();
    coarse_grid_.reset(Vec3(0.0f, 0.0f, 0.0f), Vec3(4.0f, 1.8f, 4.0f), 0.15f);
    fine_grid_.reset();
    trajectory_analyzer_.reset();
}

void Engine::set_tracker_type(TrackerType type) {
    profile_ = snoopar::get_tracker_profile(type);
    if (!observations_.empty()) {
        coarse_grid_.recompute_all(observations_, profile_);
        if (fine_grid_) {
            fine_grid_->recompute_all(observations_, profile_);
        }
    }
}

void Engine::add_observation(const Vec3& observer_pos, const Vec3& camera_gaze, float rssi, uint64_t timestamp_ns) {
    BleObservation obs{timestamp_ns, observer_pos, camera_gaze.normalized(), rssi};
    observations_.push_back(obs);
    trajectory_analyzer_.add_pose(observer_pos);

    // If this is the first observation, re-center the coarse grid forward along the user's initial gaze
    if (observations_.size() == 1) {
        Vec3 start_center = observer_pos + camera_gaze.normalized() * 0.8f;
        coarse_grid_.reset(start_center, Vec3(5.0f, 2.0f, 5.0f), 0.18f);
    }

    coarse_grid_.add_observation(obs, profile_);

    if (fine_grid_) {
        fine_grid_->add_observation(obs, profile_);
    }

    // Check automatic state transition
    Vec3 est;
    float rad = 5.0f;
    if (get_estimate(est, rad)) {
        if (state_ == SearchState::CoarseSearch && rad < 1.0f && observations_.size() >= 12) {
            state_ = SearchState::ReadyForCloseSweep;
        } else if (state_ == SearchState::CloseSweepActive && rad <= 0.35f) {
            state_ = SearchState::TargetLocked;
        }
    }
}

void Engine::start_close_sweep() {
    Vec3 coarse_pos;
    float rad = 1.0f;
    if (coarse_grid_.get_estimate(coarse_pos, rad)) {
        // Instantiate high-resolution fine grid (8 cm voxels) around the coarse quadrant
        // Adaptively size span to coarse uncertainty to prevent clipping true target
        float span_xz = std::max(1.6f, std::min(rad * 1.2f, 2.2f));
        float span_y = std::max(0.8f, std::min(rad * 0.8f, 1.4f));
        fine_grid_ = std::make_unique<BayesianGrid>(coarse_pos, Vec3(span_xz, span_y, span_xz), 0.08f);
        fine_grid_->recompute_all(observations_, profile_);
        state_ = SearchState::CloseSweepActive;
    }
}

bool Engine::get_estimate(Vec3& out_position, float& out_confidence_radius) const {
    if (fine_grid_) {
        return fine_grid_->get_estimate(out_position, out_confidence_radius);
    }
    return coarse_grid_.get_estimate(out_position, out_confidence_radius);
}

void Engine::get_voxel_cloud(std::vector<Voxel>& out_voxels, size_t max_count) const {
    if (fine_grid_) {
        fine_grid_->get_voxel_cloud(out_voxels, max_count);
    } else {
        coarse_grid_.get_voxel_cloud(out_voxels, max_count);
    }
}

bool Engine::get_bearing_to_target(
    const Vec3& current_cam_pos,
    const Vec3& current_cam_gaze,
    float& out_azimuth_deg,
    float& out_elevation_deg,
    float& out_distance_m
) const {
    Vec3 est;
    float rad = 0.0f;
    if (!get_estimate(est, rad)) {
        return false;
    }

    Vec3 delta = est - current_cam_pos;
    out_distance_m = delta.norm();
    if (out_distance_m < 1e-4f) {
        out_azimuth_deg = 0.0f;
        out_elevation_deg = 0.0f;
        return true;
    }

    // Horizontal floor plane: X=Right, Z=Depth
    float dot_horiz = current_cam_gaze.x * delta.x + current_cam_gaze.z * delta.z;
    float cross_horiz = current_cam_gaze.x * delta.z - current_cam_gaze.z * delta.x;

    float angle_rad = std::atan2(cross_horiz, dot_horiz);
    out_azimuth_deg = angle_rad * (180.0f / 3.14159265358979323846f);

    float elev_ratio = std::clamp(delta.y / out_distance_m, -1.0f, 1.0f);
    out_elevation_deg = std::asin(elev_ratio) * (180.0f / 3.14159265358979323846f);

    return true;
}

GuidanceHint Engine::get_guidance_hint() const {
    Vec3 est;
    float rad = 5.0f;
    get_estimate(est, rad);
    return trajectory_analyzer_.evaluate_guidance(state_, rad);
}

} // namespace snoopar
