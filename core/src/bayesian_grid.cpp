#include "snoopar/bayesian_grid.h"
#include "snoopar/rf_model.h"
#include <algorithm>
#include <cmath>
#include <numeric>

namespace snoopar {

BayesianGrid::BayesianGrid(Vec3 center, Vec3 span, float resolution)
    : center_(center), span_(span), resolution_(resolution) {
    build_grid();
}

void BayesianGrid::reset(Vec3 center, Vec3 span, float resolution) {
    center_ = center;
    span_ = span;
    resolution_ = resolution;
    observations_.clear();
    build_grid();
}

void BayesianGrid::build_grid() {
    grid_points_.clear();

    float half_x = span_.x * 0.5f;
    float half_y = span_.y * 0.5f;
    float half_z = span_.z * 0.5f;

    for (float x = center_.x - half_x; x <= center_.x + half_x; x += resolution_) {
        for (float y = center_.y - half_y; y <= center_.y + half_y; y += resolution_) {
            for (float z = center_.z - half_z; z <= center_.z + half_z; z += resolution_) {
                grid_points_.emplace_back(x, y, z);
            }
        }
    }

    log_likelihoods_.assign(grid_points_.size(), 0.0f);
}

void BayesianGrid::add_observation(const BleObservation& obs, const TrackerProfile& profile) {
    observations_.push_back(obs);
    update_all_voxels(profile);
}

void BayesianGrid::recompute_all(const std::vector<BleObservation>& observations, const TrackerProfile& profile) {
    observations_ = observations;
    update_all_voxels(profile);
}

void BayesianGrid::update_all_voxels(const TrackerProfile& profile) {
    if (observations_.empty()) {
        std::fill(log_likelihoods_.begin(), log_likelihoods_.end(), 0.0f);
        return;
    }

    // Evaluate closed-form marginalized A and Student-t likelihood across all voxels
    for (size_t i = 0; i < grid_points_.size(); ++i) {
        log_likelihoods_[i] = RFModel::evaluate_candidate(
            grid_points_[i],
            observations_,
            profile,
            profile.default_n
        );
    }
}

bool BayesianGrid::get_estimate(Vec3& out_position, float& out_confidence_radius) const {
    if (grid_points_.empty() || log_likelihoods_.empty() || observations_.empty()) {
        return false;
    }

    // Find the maximum a posteriori (MAP) peak
    auto max_it = std::max_element(log_likelihoods_.begin(), log_likelihoods_.end());
    size_t peak_idx = std::distance(log_likelihoods_.begin(), max_it);
    float max_log_lik = *max_it;

    // Cluster around peak (top voxels within 10 nats of peak)
    float cluster_cutoff = max_log_lik - 10.0f;
    float sum_weights = 0.0f;
    Vec3 weighted_pos(0.0f, 0.0f, 0.0f);

    std::vector<float> top_weights;
    std::vector<Vec3> top_pts;

    for (size_t i = 0; i < grid_points_.size(); ++i) {
        if (log_likelihoods_[i] >= cluster_cutoff) {
            float weight = std::exp(log_likelihoods_[i] - max_log_lik);
            sum_weights += weight;
            weighted_pos += grid_points_[i] * weight;
            top_weights.push_back(weight);
            top_pts.push_back(grid_points_[i]);
        }
    }

    if (sum_weights > 1e-8f) {
        out_position = weighted_pos / sum_weights;

        // Compute weighted variance of top cluster to derive 3-sigma confidence radius
        float weighted_var = 0.0f;
        for (size_t i = 0; i < top_pts.size(); ++i) {
            float d = (top_pts[i] - out_position).norm();
            weighted_var += (top_weights[i] / sum_weights) * (d * d);
        }
        out_confidence_radius = std::max(3.0f * std::sqrt(weighted_var), resolution_ * 1.5f);
    } else {
        out_position = grid_points_[peak_idx];
        out_confidence_radius = resolution_ * 3.0f;
    }

    return true;
}

void BayesianGrid::get_voxel_cloud(std::vector<Voxel>& out_voxels, size_t max_count) const {
    out_voxels.clear();
    if (grid_points_.empty() || log_likelihoods_.empty()) {
        return;
    }

    float max_log_lik = *std::max_element(log_likelihoods_.begin(), log_likelihoods_.end());

    struct Candidate {
        size_t idx;
        float weight;
    };

    std::vector<Candidate> candidates;
    candidates.reserve(grid_points_.size());

    for (size_t i = 0; i < grid_points_.size(); ++i) {
        float rel = log_likelihoods_[i] - max_log_lik;
        if (rel > -15.0f) { // Within 15 nats of peak
            candidates.push_back({i, std::exp(rel)});
        }
    }

    // Sort descending by probability
    std::sort(candidates.begin(), candidates.end(), [](const Candidate& a, const Candidate& b) {
        return a.weight > b.weight;
    });

    size_t count = std::min(max_count, candidates.size());
    out_voxels.reserve(count);

    for (size_t i = 0; i < count; ++i) {
        const auto& c = candidates[i];
        out_voxels.push_back({
            grid_points_[c.idx].x,
            grid_points_[c.idx].y,
            grid_points_[c.idx].z,
            c.weight // Normalized probability [0.0, 1.0]
        });
    }
}

} // namespace snoopar
