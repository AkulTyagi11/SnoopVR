#include <jni.h>
#include "snoopar/snoopar_core_c.h"
#include <vector>
#include <android/log.h>

#define TAG "SnoopAR_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeCreateEngine(
    JNIEnv* env, jobject thiz, jint tracker_type
) {
    return reinterpret_cast<jlong>(snoopar_create_engine(tracker_type));
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeDestroyEngine(
    JNIEnv* env, jobject thiz, jlong handle
) {
    snoopar_destroy_engine(reinterpret_cast<SnoopAREngineHandle>(handle));
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeResetEngine(
    JNIEnv* env, jobject thiz, jlong handle
) {
    snoopar_reset_engine(reinterpret_cast<SnoopAREngineHandle>(handle));
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeSetTrackerType(
    JNIEnv* env, jobject thiz, jlong handle, jint tracker_type
) {
    snoopar_set_tracker_type(reinterpret_cast<SnoopAREngineHandle>(handle), tracker_type);
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeAddObservation(
    JNIEnv* env, jobject thiz, jlong handle,
    jfloat obs_x, jfloat obs_y, jfloat obs_z,
    jfloat gaze_x, jfloat gaze_y, jfloat gaze_z,
    jfloat rssi, jlong timestamp_ns
) {
    snoopar_add_observation(
        reinterpret_cast<SnoopAREngineHandle>(handle),
        obs_x, obs_y, obs_z,
        gaze_x, gaze_y, gaze_z,
        rssi,
        static_cast<uint64_t>(timestamp_ns)
    );
}

JNIEXPORT jint JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetSearchState(
    JNIEnv* env, jobject thiz, jlong handle
) {
    return snoopar_get_search_state(reinterpret_cast<SnoopAREngineHandle>(handle));
}

JNIEXPORT jint JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetGuidanceHint(
    JNIEnv* env, jobject thiz, jlong handle
) {
    return snoopar_get_guidance_hint(reinterpret_cast<SnoopAREngineHandle>(handle));
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeStartCloseSweep(
    JNIEnv* env, jobject thiz, jlong handle
) {
    snoopar_start_close_sweep(reinterpret_cast<SnoopAREngineHandle>(handle));
}

JNIEXPORT jlong JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetObservationCount(
    JNIEnv* env, jobject thiz, jlong handle
) {
    return static_cast<jlong>(snoopar_get_observation_count(reinterpret_cast<SnoopAREngineHandle>(handle)));
}

JNIEXPORT jfloatArray JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetEstimate(
    JNIEnv* env, jobject thiz, jlong handle
) {
    float x = 0.0f, y = 0.0f, z = 0.0f, radius = 0.0f;
    int32_t ok = snoopar_get_estimate(reinterpret_cast<SnoopAREngineHandle>(handle), &x, &y, &z, &radius);
    if (!ok) {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) return nullptr;

    jfloat elements[4] = {x, y, z, radius};
    env->SetFloatArrayRegion(result, 0, 4, elements);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetVoxelCloud(
    JNIEnv* env, jobject thiz, jlong handle, jint max_count
) {
    if (max_count <= 0) return nullptr;

    std::vector<SnoopARVoxel> voxels(static_cast<size_t>(max_count));
    size_t actual_count = snoopar_get_voxel_cloud(
        reinterpret_cast<SnoopAREngineHandle>(handle),
        voxels.data(),
        static_cast<size_t>(max_count)
    );

    if (actual_count == 0) return nullptr;

    jsize total_floats = static_cast<jsize>(actual_count * 4);
    jfloatArray result = env->NewFloatArray(total_floats);
    if (result == nullptr) return nullptr;

    std::vector<jfloat> flat_data(total_floats);
    for (size_t i = 0; i < actual_count; ++i) {
        flat_data[i * 4 + 0] = voxels[i].x;
        flat_data[i * 4 + 1] = voxels[i].y;
        flat_data[i * 4 + 2] = voxels[i].z;
        flat_data[i * 4 + 3] = voxels[i].probability;
    }

    env->SetFloatArrayRegion(result, 0, total_floats, flat_data.data());
    return result;
}

} // extern "C"
