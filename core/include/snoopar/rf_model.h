#pragma once

#include "snoopar/types.h"
#include <vector>

namespace snoopar {

class RFModel {
public:
    static constexpr float D_MIN = 0.05f;        // 5 cm minimum distance to avoid log singularity
    static constexpr float STUDENT_T_NU = 3.0f;  // Degrees of freedom for heavy-tailed multipath tolerance
    static constexpr float DEFAULT_SIGMA_OBS = 3.5f; // Standard deviation of measurement noise (dB)
    static constexpr float MAX_BODY_LOSS_DB = 8.0f;  // Torso attenuation when facing away (dB)

    // Calculates body blocking attenuation given camera gaze vector
    static float compute_body_loss(const Vec3& target, const Vec3& obs_pos, const Vec3& camera_gaze);

    // Calculates geometric path loss: -10 * n * log10(max(d, d_min))
    static float compute_path_loss(const Vec3& target, const Vec3& obs_pos, float n);

    // Evaluates Student-t log likelihood: log(p(z | expected))
    static float student_t_log_likelihood(float residual, float nu = STUDENT_T_NU, float sigma = DEFAULT_SIGMA_OBS);

    // Evaluates total log likelihood for a candidate point given all observations
    // with transmit power A conditioned on device prior or analytically marginalized
    static float evaluate_candidate(
        const Vec3& candidate,
        const std::vector<BleObservation>& observations,
        const TrackerProfile& profile,
        float n_assumed
    );
};

} // namespace snoopar
