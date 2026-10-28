#include "snoopar/trajectory_analyzer.h"
#include <algorithm>
#include <cmath>

namespace snoopar {

void TrajectoryAnalyzer::add_pose(const Vec3& pos) {
    poses_.push_back(pos);
}

void TrajectoryAnalyzer::reset() {
    poses_.clear();
}

float TrajectoryAnalyzer::get_path_length() const {
    if (poses_.size() < 2) return 0.0f;
    float len = 0.0f;
    for (size_t i = 1; i < poses_.size(); ++i) {
        len += (poses_[i] - poses_[i - 1]).norm();
    }
    return len;
}

float TrajectoryAnalyzer::get_vertical_span() const {
    if (poses_.empty()) return 0.0f;
    // In ARCore, Y is the vertical elevation axis
    float min_y = poses_[0].y;
    float max_y = poses_[0].y;
    for (const auto& p : poses_) {
        min_y = std::min(min_y, p.y);
        max_y = std::max(max_y, p.y);
    }
    return max_y - min_y;
}

float TrajectoryAnalyzer::get_planar_aspect_ratio() const {
    if (poses_.size() < 4) return 1.0f;

    // In ARCore, the horizontal floor plane is defined by (X, Z)
    float mean_x = 0.0f;
    float mean_z = 0.0f;
    for (const auto& p : poses_) {
        mean_x += p.x;
        mean_z += p.z;
    }
    mean_x /= poses_.size();
    mean_z /= poses_.size();

    // 2x2 horizontal (X, Z) covariance matrix
    float cxx = 0.0f, czz = 0.0f, cxz = 0.0f;
    for (const auto& p : poses_) {
        float dx = p.x - mean_x;
        float dz = p.z - mean_z;
        cxx += dx * dx;
        czz += dz * dz;
        cxz += dx * dz;
    }

    // Eigenvalues of 2x2 matrix: lambda = (tr +- sqrt(tr^2 - 4*det)) / 2
    float tr = cxx + czz;
    float det = cxx * czz - cxz * cxz;
    float disc = std::max(tr * tr - 4.0f * det, 0.0f);
    float l1 = 0.5f * (tr + std::sqrt(disc));
    float l2 = 0.5f * (tr - std::sqrt(disc));

    if (l1 < 1e-6f) return 0.0f;
    return std::sqrt(std::max(l2, 0.0f) / l1); // Aspect ratio: 0 = pure straight line, 1 = circular
}

GuidanceHint TrajectoryAnalyzer::evaluate_guidance(SearchState current_state, float current_confidence_radius) const {
    if (poses_.size() < 6) {
        return GuidanceHint::ContinueSweep;
    }

    if (current_state == SearchState::CoarseSearch) {
        // Collinearity check: if user is walking in a straight line, prompt to step sideways
        if (poses_.size() >= 8 && get_planar_aspect_ratio() < 0.18f) {
            return GuidanceHint::StepSideways;
        }

        // Height variation check
        if (poses_.size() >= 10 && get_vertical_span() < 0.15f) {
            return GuidanceHint::ChangeHeight;
        }

        // If coarse quadrant is well identified and confidence is tight enough
        if (poses_.size() >= 12 && current_confidence_radius < 1.1f) {
            return GuidanceHint::ApproachSurface;
        }

        return GuidanceHint::ContinueSweep;
    }

    if (current_state == SearchState::CloseSweepActive) {
        if (current_confidence_radius <= 0.35f) {
            return GuidanceHint::TargetLocked;
        }
        return GuidanceHint::ContinueSweep;
    }

    return GuidanceHint::ContinueSweep;
}

} // namespace snoopar
