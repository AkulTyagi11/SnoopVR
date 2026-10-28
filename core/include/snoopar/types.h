#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <cmath>

namespace snoopar {

struct Vec3 {
    float x{0.0f};
    float y{0.0f};
    float z{0.0f};

    constexpr Vec3() = default;
    constexpr Vec3(float x_, float y_, float z_) : x(x_), y(y_), z(z_) {}

    Vec3 operator+(const Vec3& o) const { return {x + o.x, y + o.y, z + o.z}; }
    Vec3 operator-(const Vec3& o) const { return {x - o.x, y - o.y, z - o.z}; }
    Vec3 operator*(float s) const { return {x * s, y * s, z * s}; }
    Vec3 operator/(float s) const { return {x / s, y / s, z / s}; }

    Vec3& operator+=(const Vec3& o) { x += o.x; y += o.y; z += o.z; return *this; }
    Vec3& operator-=(const Vec3& o) { x -= o.x; y -= o.y; z -= o.z; return *this; }

    float dot(const Vec3& o) const { return x * o.x + y * o.y + z * o.z; }
    float norm_sq() const { return dot(*this); }
    float norm() const { return std::sqrt(norm_sq()); }

    Vec3 normalized() const {
        float n = norm();
        return (n > 1e-6f) ? (*this / n) : Vec3(0.0f, 0.0f, 0.0f);
    }
};

struct Quaternion {
    float w{1.0f};
    float x{0.0f};
    float y{0.0f};
    float z{0.0f};

    // Computes camera optical forward vector (-Z in standard ARKit/ARCore camera frame)
    Vec3 forward() const {
        return Vec3(
            -2.0f * (x * z + w * y),
            -2.0f * (y * z - w * x),
            -(1.0f - 2.0f * (x * x + y * y))
        ).normalized();
    }
};

struct CameraPose {
    uint64_t timestamp_ns{0};
    Vec3 position;
    Quaternion orientation;
};

struct BleObservation {
    uint64_t timestamp_ns{0};
    Vec3 observer_pos;
    Vec3 camera_gaze; // Unit direction the phone was pointing
    float rssi{0.0f}; // In dBm
};

enum class TrackerType : int32_t {
    AirTag = 0,
    SmartTag = 1,
    Tile = 2,
    Generic = 3
};

struct TrackerProfile {
    TrackerType type;
    const char* name;
    float prior_A;      // Expected RSSI at 1 meter (dBm)
    float sigma_A;      // Prior uncertainty on A (dB)
    float default_n;    // Expected path-loss exponent
};

inline TrackerProfile get_tracker_profile(TrackerType type) {
    switch (type) {
        case TrackerType::AirTag:
            return {TrackerType::AirTag, "Apple AirTag", -58.5f, 1.8f, 2.7f};
        case TrackerType::SmartTag:
            return {TrackerType::SmartTag, "Samsung SmartTag", -59.0f, 1.8f, 2.7f};
        case TrackerType::Tile:
            return {TrackerType::Tile, "Tile Mate/Pro", -62.0f, 2.2f, 2.7f};
        case TrackerType::Generic:
        default:
            return {TrackerType::Generic, "Generic BLE Tag", -60.0f, 4.0f, 2.7f};
    }
}

enum class SearchState : int32_t {
    CoarseSearch = 0,         // Stage 1: Distant sweep (1.5-2.5m) to locate coarse quadrant
    ReadyForCloseSweep = 1,   // Coarse quadrant identified; prompt user to move closer
    CloseSweepActive = 2,     // Stage 2: Near-field sweep (30-50cm) along suspected surface
    TargetLocked = 3          // Target locked within precision threshold (< 25-35 cm)
};

enum class GuidanceHint : int32_t {
    ContinueSweep = 0,
    StepSideways = 1,         // Motion is collinear / straight line
    ChangeHeight = 2,         // Scan higher or lower to constrain vertical axis
    ApproachSurface = 3,      // Ready to begin close sweep along surface
    TargetLocked = 4
};

struct Voxel {
    float x;
    float y;
    float z;
    float probability; // Normalized density [0.0, 1.0] for AR rendering
};

} // namespace snoopar
