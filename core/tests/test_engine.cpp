#include "snoopar/engine.h"
#include "snoopar/snoopar_core_c.h"
#include <iostream>
#include <cstdlib>
#include <cmath>

#define CHECK(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "[FAIL] Line " << __LINE__ << ": " << msg << std::endl; \
            std::exit(1); \
        } \
    } while (0)

using namespace snoopar;

int main() {
    std::cout << "==================================================\n";
    std::cout << " SnoopAR Core Engine C++ Verification Suite\n";
    std::cout << " Coordinate Convention: ARCore Y-Up (X=Right, Y=Up, Z=Depth)\n";
    std::cout << "==================================================\n";

    // 1. Test C++ Engine Lifecycle
    Engine engine(TrackerType::AirTag);
    CHECK(engine.get_search_state() == SearchState::CoarseSearch, "Initial state must be CoarseSearch");
    CHECK(engine.get_observation_count() == 0, "Initial observation count must be 0");
    std::cout << "[PASS] Test 1: Engine initialization\n";

    // Ground truth target: in front of car bumper in ARCore frame:
    // X = 1.10m (right of start), Y = 0.35m (height above ground), Z = -1.20m (forward)
    Vec3 true_target(1.10f, 0.35f, -1.20f);

    // TAG POWER MISMATCH TEST:
    // Profile assumes prior_A = -58.5 dBm.
    // We intentionally simulate a tag that is 5 dB weaker (-63.5 dBm) with n = 2.9 to test marginalization!
    float real_A = -63.5f;
    float real_n = 2.9f;

    std::cout << "[INFO] Simulating Tag with 5.0 dB transmit power mismatch (A=" << real_A << ", n=" << real_n << ")\n";

    // 2. Feed Coarse Trajectory (Walking arc in front of vehicle)
    for (int i = 0; i < 20; ++i) {
        float angle = -1.0f + i * 0.1f;
        // User moves along X and Z, holding phone at Y = 0.85m with natural sway
        float x = 2.0f * std::sin(angle) + 0.2f;
        float y = 0.85f + 0.10f * std::sin(angle * 3.0f);
        float z = -2.0f * std::cos(angle) + 0.5f;
        Vec3 obs_pos(x, y, z);

        Vec3 gaze = (true_target - obs_pos).normalized();
        float dist = std::max((true_target - obs_pos).norm(), 0.05f);
        float rssi = real_A - 10.0f * real_n * std::log10(dist);

        engine.add_observation(obs_pos, gaze, rssi);
    }
    CHECK(engine.get_observation_count() == 20, "Must have 20 observations");
    std::cout << "[PASS] Test 2: Ingested 20 coarse observations\n";

    Vec3 coarse_est;
    float coarse_rad = 0.0f;
    bool ok = engine.get_estimate(coarse_est, coarse_rad);
    CHECK(ok, "Coarse estimate must succeed");
    float coarse_err = (coarse_est - true_target).norm();
    std::cout << "[INFO] Coarse Estimate: [" << coarse_est.x << ", " << coarse_est.y << ", " << coarse_est.z 
              << "] (Error: " << (coarse_err * 100.0f) << " cm, Radius: " << (coarse_rad * 100.0f) << " cm)\n";

    // 3. Test Transition to Close Sweep
    engine.start_close_sweep();
    CHECK(engine.get_search_state() == SearchState::CloseSweepActive, "Must be in CloseSweepActive");
    std::cout << "[PASS] Test 3: Transitioned to Stage 2 Close Sweep (5 cm high-res grid)\n";

    // 4. Feed Close Sweep Trajectory (along suspect bumper surface)
    for (int i = 0; i < 15; ++i) {
        float x = 0.5f + i * 0.08f;
        // Phone swept 30cm in front of bumper (Z = -0.90m) at two heights
        for (float y_sweep : {0.25f, 0.45f}) {
            Vec3 close_pos(x, y_sweep, -0.90f);
            Vec3 gaze(0.0f, 0.0f, -1.0f); // Pointing forward into bumper
            float dist = std::max((true_target - close_pos).norm(), 0.05f);
            float rssi = real_A - 10.0f * real_n * std::log10(dist);

            engine.add_observation(close_pos, gaze, rssi);
        }
    }

    Vec3 fine_est;
    float fine_rad = 0.0f;
    ok = engine.get_estimate(fine_est, fine_rad);
    CHECK(ok, "Fine estimate must succeed");
    float fine_err = (fine_est - true_target).norm();
    std::cout << "[INFO] Fine Estimate:   [" << fine_est.x << ", " << fine_est.y << ", " << fine_est.z 
              << "] (Error: " << (fine_err * 100.0f) << " cm, Radius: " << (fine_rad * 100.0f) << " cm)\n";

    CHECK(fine_err < 0.35f, "Fine error must be within 35 cm even with 5 dB transmit power mismatch");
    std::cout << "[PASS] Test 4: Closed-form marginalized A achieved < 35 cm lock despite 5 dB mismatch!\n";

    // 5. Test Voxel Cloud Extraction for AR
    std::vector<Voxel> voxels;
    engine.get_voxel_cloud(voxels, 50);
    CHECK(!voxels.empty(), "Voxel cloud must not be empty");
    CHECK(voxels[0].probability >= 0.95f, "Peak voxel must have probability >= 0.95");
    std::cout << "[PASS] Test 5: Extracted " << voxels.size() << " AR probability voxels\n";

    // 6. Test C-ABI Functions
    SnoopAREngineHandle c_engine = snoopar_create_engine(0); // AirTag
    CHECK(c_engine != nullptr, "C handle must not be null");
    snoopar_add_observation(c_engine, 1.0f, 0.8f, -1.0f, 0.0f, 0.0f, -1.0f, -65.0f, 1000);
    CHECK(snoopar_get_observation_count(c_engine) == 1, "Count must be 1");
    snoopar_destroy_engine(c_engine);
    std::cout << "[PASS] Test 6: C-ABI FFI wrapper verified\n";

    // 7. Test Directional Bearing Calculation
    float azimuth = 0.0f, elevation = 0.0f, dist = 0.0f;
    // Current phone at (0, 0.8, 0) looking forward along -Z (gaze = 0, 0, -1)
    // Target is around (1.2, 0.45, -0.8) -> delta.x = +1.2 (to the right!), delta.z = -0.8 (forward)
    bool bearing_ok = engine.get_bearing_to_target(Vec3(0.0f, 0.8f, 0.0f), Vec3(0.0f, 0.0f, -1.0f), azimuth, elevation, dist);
    CHECK(bearing_ok, "Bearing calculation must succeed when estimate exists");
    CHECK(dist > 0.5f && dist < 2.5f, "Distance to target must be within reasonable range");
    CHECK(azimuth > 20.0f && azimuth < 85.0f, "Target at positive X must produce positive right azimuth angle");
    std::cout << "[PASS] Test 7: Directional bearing verified (Azimuth: " << azimuth << " deg, Dist: " << dist << " m)\n";

    std::cout << "==================================================\n";
    std::cout << " All 7 Core Engine Tests Passed with ARCore Y-Up Axes!\n";
    std::cout << "==================================================\n";
    return 0;
}
