#include "snoopar/engine.h"
#include <iostream>

namespace snoopar {

Engine::Engine(TrackerType type)
    : profile_(snoopar::get_tracker_profile(type)),
      coarse_grid_(Vec3(0.0f, 0.0f, 0.0f), Vec3(3.5f, 3.5f, 1.6f), 0.15f) {
}

void Engine::reset() {
    state_ = SearchState::CoarseSearch;
    observations_.clear();
    coarse_grid_.reset(Vec3(0.0f, 0.0f, 0.0f), Vec3(3.5f, 3.5f, 1.6f), 0.15f);
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
        // Instantiate high-resolution fine grid (5 cm voxels) around the coarse quadrant
        fine_grid_ = std::make_unique<BayesianGrid>(coarse_pos, Vec3(1.4f, 1.4f, 0.8f), 0.05f);
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

GuidanceHint Engine::get_guidance_hint() const {
    Vec3 est;
    float rad = 5.0f;
    get_estimate(est, rad);
    return trajectory_analyzer_.evaluate_guidance(state_, rad);
}

} // namespace snoopar
