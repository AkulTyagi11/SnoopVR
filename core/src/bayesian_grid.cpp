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
    // with multi-hypothesis marginalization over path-loss exponent n (e.g. n - 0.35, n, n + 0.35)
    const float n_hypotheses[3] = {
        profile.default_n - 0.35f,
        profile.default_n,
        profile.default_n + 0.35f
    };
    const float log_prior_n[3] = {
        -1.38629436f, // ln(0.25)
        -0.69314718f, // ln(0.50)
        -1.38629436f  // ln(0.25)
    };

    for (size_t i = 0; i < grid_points_.size(); ++i) {
        float l_hyps[3];
        float max_l = -1e9f;
        for (int j = 0; j < 3; ++j) {
            l_hyps[j] = RFModel::evaluate_candidate(
                grid_points_[i],
                observations_,
                profile,
                n_hypotheses[j]
            ) + log_prior_n[j];
            if (l_hyps[j] > max_l) {
                max_l = l_hyps[j];
            }
        }

        // Log-sum-exp over n hypotheses
        float sum_exp = 0.0f;
        for (int j = 0; j < 3; ++j) {
            sum_exp += std::exp(l_hyps[j] - max_l);
        }
        float log_lik = max_l + std::log(sum_exp);

        // Apply surface plane prior constraint if active
        if (has_plane_constraint_) {
            Vec3 diff = grid_points_[i] - plane_point_;
            float dist_to_plane = std::abs(diff.dot(plane_normal_));
            float plane_prior = -0.5f * (dist_to_plane / plane_sigma_) * (dist_to_plane / plane_sigma_);
            log_lik += plane_prior;
        }

        log_likelihoods_[i] = log_lik;
    }
}

void BayesianGrid::set_surface_plane(const Vec3& plane_point, const Vec3& plane_normal, float sigma_meters) {
    has_plane_constraint_ = true;
    plane_point_ = plane_point;
    plane_normal_ = plane_normal.normalized();
    plane_sigma_ = std::max(sigma_meters, 0.02f);
}

void BayesianGrid::clear_surface_plane() {
    has_plane_constraint_ = false;
}

bool BayesianGrid::get_estimate(Vec3& out_position, float& out_confidence_radius) const {
    if (grid_points_.empty() || log_likelihoods_.empty() || observations_.empty()) {
        return false;
    }

    // Find the maximum a posteriori (MAP) peak
    auto max_it = std::max_element(log_likelihoods_.begin(), log_likelihoods_.end());
    size_t peak_idx = std::distance(log_likelihoods_.begin(), max_it);
    float max_log_lik = *max_it;

    // Cluster around peak (top voxels within 3.5 nats of peak for sharp localization)
    float cluster_cutoff = max_log_lik - (has_plane_constraint_ ? 2.5f : 3.5f);
    float sum_weights = 0.0f;
    Vec3 weighted_pos(0.0f, 0.0f, 0.0f);

    std::vector<float> top_weights;
    std::vector<Vec3> top_pts;

    for (size_t i = 0; i < grid_points_.size(); ++i) {
        if (log_likelihoods_[i] >= cluster_cutoff) {
            float rel = log_likelihoods_[i] - max_log_lik;
            float weight = std::exp(rel);
            // Non-linear peak sharpening concentrates density on peak modes
            float sharp_weight = weight * weight;
            sum_weights += sharp_weight;
            weighted_pos += grid_points_[i] * sharp_weight;
            top_weights.push_back(sharp_weight);
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

bool BayesianGrid::is_peak_on_boundary() const {
    if (grid_points_.empty() || log_likelihoods_.empty()) {
        return false;
    }
    auto max_it = std::max_element(log_likelihoods_.begin(), log_likelihoods_.end());
    size_t peak_idx = std::distance(log_likelihoods_.begin(), max_it);
    const Vec3& p = grid_points_[peak_idx];

    float half_x = span_.x * 0.5f;
    float half_y = span_.y * 0.5f;
    float half_z = span_.z * 0.5f;
    float tol = resolution_ * 1.05f;

    if (std::abs(p.x - (center_.x - half_x)) <= tol || std::abs(p.x - (center_.x + half_x)) <= tol) return true;
    if (std::abs(p.y - (center_.y - half_y)) <= tol || std::abs(p.y - (center_.y + half_y)) <= tol) return true;
    if (std::abs(p.z - (center_.z - half_z)) <= tol || std::abs(p.z - (center_.z + half_z)) <= tol) return true;

    return false;
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
