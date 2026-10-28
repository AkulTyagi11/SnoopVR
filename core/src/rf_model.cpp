#include "snoopar/rf_model.h"
#include <algorithm>
#include <cmath>

namespace snoopar {

float RFModel::compute_body_loss(const Vec3& target, const Vec3& obs_pos, const Vec3& camera_gaze) {
    Vec3 disp = target - obs_pos;
    float dist = std::max(disp.norm(), D_MIN);
    Vec3 unit_disp = disp / dist;

    // Dot product between camera gaze and vector to target
    float cos_angle = camera_gaze.dot(unit_disp);

    // If cos_angle < 0, target is behind the phone / user is standing between phone and target
    if (cos_angle < 0.0f) {
        float factor = (1.0f - cos_angle) * 0.5f;
        return MAX_BODY_LOSS_DB * factor * factor;
    }
    return 0.0f;
}

float RFModel::compute_path_loss(const Vec3& target, const Vec3& obs_pos, float n) {
    float dist = std::max((target - obs_pos).norm(), D_MIN);
    return -10.0f * n * std::log10(dist);
}

float RFModel::student_t_log_likelihood(float residual, float nu, float sigma) {
    float scale_sq = nu * (sigma * sigma);
    return -0.5f * (nu + 1.0f) * std::log(1.0f + (residual * residual) / scale_sq);
}

float RFModel::evaluate_candidate(
    const Vec3& candidate,
    const std::vector<BleObservation>& observations,
    const TrackerProfile& profile,
    float n_assumed
) {
    if (observations.empty()) {
        return 0.0f;
    }

    float total_log_lik = 0.0f;

    // Closed-form Bayesian marginalization of A:
    // RSSI_k = A + g_k + noise_k, where g_k = path_loss_k - body_loss_k
    // Under Gaussian prior A ~ N(mu_A, sigma_A^2):
    // The conditional posterior mean A_hat can be computed and plugged in,
    // or we evaluate likelihood using the informative prior mode.
    float sum_g = 0.0f;
    float sum_z_minus_g = 0.0f;
    float K = static_cast<float>(observations.size());

    std::vector<float> g_values;
    g_values.reserve(observations.size());

    for (const auto& obs : observations) {
        float path_loss = compute_path_loss(candidate, obs.observer_pos, n_assumed);
        float body_loss = compute_body_loss(candidate, obs.observer_pos, obs.camera_gaze);
        float g = path_loss - body_loss;
        g_values.push_back(g);
        sum_g += g;
        sum_z_minus_g += (obs.rssi - g);
    }

    // Posterior estimate of A given candidate geometry
    float prior_prec = 1.0f / (profile.sigma_A * profile.sigma_A);
    float obs_prec = 1.0f / (DEFAULT_SIGMA_OBS * DEFAULT_SIGMA_OBS);
    float post_prec = prior_prec + K * obs_prec;
    float post_var = 1.0f / post_prec;
    float A_hat = post_var * (profile.prior_A * prior_prec + obs_prec * sum_z_minus_g);

    // Penalize discrepancy from device factory prior
    float prior_penalty = -0.5f * std::pow((A_hat - profile.prior_A) / profile.sigma_A, 2.0f);
    total_log_lik += prior_penalty;

    // Accumulate Student-t likelihood for each observation
    for (size_t k = 0; k < observations.size(); ++k) {
        float expected_rssi = A_hat + g_values[k];
        float residual = observations[k].rssi - expected_rssi;
        total_log_lik += student_t_log_likelihood(residual, STUDENT_T_NU, DEFAULT_SIGMA_OBS);
    }

    return total_log_lik;
}

} // namespace snoopar
