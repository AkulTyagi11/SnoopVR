#pragma once

#include "snoopar/types.h"
#include <vector>

namespace snoopar {

class TrajectoryAnalyzer {
public:
    TrajectoryAnalyzer() = default;

    void add_pose(const Vec3& pos);
    void reset();

    GuidanceHint evaluate_guidance(SearchState current_state, float current_confidence_radius) const;

    size_t get_sample_count() const { return poses_.size(); }
    float get_path_length() const;
    float get_planar_aspect_ratio() const; // Checks for collinearity (straight line)
    float get_vertical_span() const;

private:
    std::vector<Vec3> poses_;
};

} // namespace snoopar
