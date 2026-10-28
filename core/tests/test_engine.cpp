#include "snoopar/engine.h"
#include "snoopar/snoopar_core_c.h"
#include <iostream>
#include <cassert>
#include <cmath>

using namespace snoopar;

int main() {
    std::cout << "==================================================\n";
    std::cout << " 📡 SnoopAR Core Engine C++ Test Suite\n";
    std::cout << "==================================================\n";

    // 1. Test C++ Engine Lifecycle
    Engine engine(TrackerType::AirTag);
    assert(engine.get_search_state() == SearchState::CoarseSearch);
    assert(engine.get_observation_count() == 0);
    std::cout << "[PASS] Engine initialization (AirTag profile loaded)\n";

    // Ground truth target at [1.10, 0.40, 0.30]
    Vec3 true_target(1.10f, 0.40f, 0.30f);
    float true_A = -58.5f;
    float true_n = 2.7f;

    // 2. Feed Coarse Trajectory (walking an arc around vehicle front)
    for (int i = 0; i < 20; ++i) {
        float angle = -1.0f + i * 0.1f;
        float x = 2.0f * std::sin(angle) + 0.2f;
        float y = -2.0f * std::cos(angle) + 1.2f;
        float z = 0.8f + 0.15f * std::sin(angle * 3.0f);
        Vec3 obs_pos(x, y, z);

        Vec3 gaze = (Vec3(0.5f, 0.5f, 0.3f) - obs_pos).normalized();
        float dist = std::max((true_target - obs_pos).norm(), 0.05f);
        float rssi = true_A - 10.0f * true_n * std::log10(dist);

        engine.add_observation(obs_pos, gaze, rssi);
    }
    assert(engine.get_observation_count() == 20);
    std::cout << "[PASS] Ingested 20 coarse observations\n";

    Vec3 coarse_est;
    float coarse_rad = 0.0f;
    bool ok = engine.get_estimate(coarse_est, coarse_rad);
    assert(ok);
    float coarse_err = (coarse_est - true_target).norm();
    std::cout << "[INFO] Coarse Estimate: [" << coarse_est.x << ", " << coarse_est.y << ", " << coarse_est.z 
              << "] (Error: " << (coarse_err * 100.0f) << " cm, Radius: " << (coarse_rad * 100.0f) << " cm)\n";

    // 3. Test Transition to Close Sweep
    engine.start_close_sweep();
    assert(engine.get_search_state() == SearchState::CloseSweepActive);
    std::cout << "[PASS] Transitioned to Stage 2 Close Sweep (5 cm high-res grid active)\n";

    // 4. Feed Close Sweep Trajectory (along suspect bumper surface)
    for (int i = 0; i < 15; ++i) {
        float x = 0.5f + i * 0.08f;
        Vec3 close_pos(x, 0.10f, 0.30f); // 30 cm in front of bumper
        Vec3 gaze(0.0f, 1.0f, 0.0f);
        float dist = std::max((true_target - close_pos).norm(), 0.05f);
        float rssi = true_A - 10.0f * true_n * std::log10(dist);

        engine.add_observation(close_pos, gaze, rssi);
    }

    Vec3 fine_est;
    float fine_rad = 0.0f;
    ok = engine.get_estimate(fine_est, fine_rad);
    assert(ok);
    float fine_err = (fine_est - true_target).norm();
    std::cout << "[INFO] Fine Estimate:   [" << fine_est.x << ", " << fine_est.y << ", " << fine_est.z 
              << "] (Error: " << (fine_err * 100.0f) << " cm, Radius: " << (fine_rad * 100.0f) << " cm)\n";
    assert(fine_err < 0.35f); // Must be within 35 cm in clean test
    std::cout << "[PASS] Coarse-to-Fine search achieved < 35 cm target lock!\n";

    // 5. Test Voxel Cloud Extraction for AR
    std::vector<Voxel> voxels;
    engine.get_voxel_cloud(voxels, 50);
    assert(!voxels.empty());
    assert(voxels[0].probability >= 0.99f); // Peak voxel should have probability ~1.0
    std::cout << "[PASS] Extracted " << voxels.size() << " AR probability voxels (Peak weight: " 
              << voxels[0].probability << ")\n";

    // 6. Test C-ABI Functions
    SnoopAREngineHandle c_engine = snoopar_create_engine(0); // AirTag
    assert(c_engine != nullptr);
    snoopar_add_observation(c_engine, 1.0f, 0.0f, 0.8f, 0.0f, 1.0f, 0.0f, -65.0f, 1000);
    assert(snoopar_get_observation_count(c_engine) == 1);
    snoopar_destroy_engine(c_engine);
    std::cout << "[PASS] C-ABI FFI wrapper verified\n";

    std::cout << "==================================================\n";
    std::cout << " 🎉 All 6 Core Engine Tests Passed Successfully!\n";
    std::cout << "==================================================\n";
    return 0;
}
