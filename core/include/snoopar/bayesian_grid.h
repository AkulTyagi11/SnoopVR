#pragma once

#include "snoopar/types.h"
#include <vector>

namespace snoopar {

class BayesianGrid {
public:
    BayesianGrid(
        Vec3 center = Vec3(0.0f, 0.0f, 0.0f),
        Vec3 span = Vec3(3.5f, 1.6f, 3.5f), // ARCore: X=lateral, Y=height, Z=depth
        float resolution = 0.15f
    );

    void reset(Vec3 center, Vec3 span, float resolution);

    // Ingests a new observation and updates voxel log-likelihoods
    void add_observation(const BleObservation& obs, const TrackerProfile& profile);

    // Re-evaluates entire observation history (e.g. when transitioning to fine grid)
    void recompute_all(const std::vector<BleObservation>& observations, const TrackerProfile& profile);

    // Extracts peak candidate position and estimated 3-sigma confidence radius
    bool get_estimate(Vec3& out_position, float& out_confidence_radius) const;

    // Returns voxels above a probability threshold for AR point cloud rendering
    void get_voxel_cloud(std::vector<Voxel>& out_voxels, size_t max_count = 200) const;

    // Surface plane prior constraint (e.g. detected car bumper, wall, or floor)
    void set_surface_plane(const Vec3& plane_point, const Vec3& plane_normal, float sigma_meters = 0.08f);
    void clear_surface_plane();
    bool has_surface_plane() const { return has_plane_constraint_; }

    size_t get_voxel_count() const { return grid_points_.size(); }
    float get_resolution() const { return resolution_; }
    Vec3 get_center() const { return center_; }

private:
    void build_grid();
    void update_all_voxels(const TrackerProfile& profile);

    Vec3 center_;
    Vec3 span_;
    float resolution_{0.15f};

    bool has_plane_constraint_{false};
    Vec3 plane_point_{0.0f, 0.0f, 0.0f};
    Vec3 plane_normal_{0.0f, 1.0f, 0.0f};
    float plane_sigma_{0.08f};

    std::vector<Vec3> grid_points_;
    std::vector<float> log_likelihoods_;
    std::vector<BleObservation> observations_;
};

} // namespace snoopar
