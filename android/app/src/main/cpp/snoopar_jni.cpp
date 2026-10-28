#include <jni.h>
#include "snoopar/snoopar_core_c.h"
#include <vector>
#include <exception>
#include <android/log.h>

#define TAG "SnoopAR_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void throwJavaException(JNIEnv* env, const char* message) {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
    }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeCreateEngine(
    JNIEnv* env, jobject thiz, jint tracker_type
) {
    try {
        return reinterpret_cast<jlong>(snoopar_create_engine(tracker_type));
    } catch (const std::exception& e) {
        LOGE("nativeCreateEngine exception: %s", e.what());
        throwJavaException(env, e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeDestroyEngine(
    JNIEnv* env, jobject thiz, jlong handle
) {
    if (handle == 0) return;
    try {
        snoopar_destroy_engine(reinterpret_cast<SnoopAREngineHandle>(handle));
    } catch (const std::exception& e) {
        LOGE("nativeDestroyEngine exception: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeResetEngine(
    JNIEnv* env, jobject thiz, jlong handle
) {
    if (handle == 0) return;
    try {
        snoopar_reset_engine(reinterpret_cast<SnoopAREngineHandle>(handle));
    } catch (const std::exception& e) {
        LOGE("nativeResetEngine exception: %s", e.what());
        throwJavaException(env, e.what());
    }
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeSetTrackerType(
    JNIEnv* env, jobject thiz, jlong handle, jint tracker_type
) {
    if (handle == 0) return;
    try {
        snoopar_set_tracker_type(reinterpret_cast<SnoopAREngineHandle>(handle), tracker_type);
    } catch (const std::exception& e) {
        LOGE("nativeSetTrackerType exception: %s", e.what());
        throwJavaException(env, e.what());
    }
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeAddObservation(
    JNIEnv* env, jobject thiz, jlong handle,
    jfloat obs_x, jfloat obs_y, jfloat obs_z,
    jfloat gaze_x, jfloat gaze_y, jfloat gaze_z,
    jfloat rssi, jlong timestamp_ns
) {
    if (handle == 0) return;
    try {
        snoopar_add_observation(
            reinterpret_cast<SnoopAREngineHandle>(handle),
            obs_x, obs_y, obs_z,
            gaze_x, gaze_y, gaze_z,
            rssi,
            static_cast<uint64_t>(timestamp_ns)
        );
    } catch (const std::exception& e) {
        LOGE("nativeAddObservation exception: %s", e.what());
        throwJavaException(env, e.what());
    }
}

JNIEXPORT jint JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetSearchState(
    JNIEnv* env, jobject thiz, jlong handle
) {
    if (handle == 0) return 0;
    try {
        return snoopar_get_search_state(reinterpret_cast<SnoopAREngineHandle>(handle));
    } catch (const std::exception& e) {
        LOGE("nativeGetSearchState exception: %s", e.what());
        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetGuidanceHint(
    JNIEnv* env, jobject thiz, jlong handle
) {
    if (handle == 0) return 0;
    try {
        return snoopar_get_guidance_hint(reinterpret_cast<SnoopAREngineHandle>(handle));
    } catch (const std::exception& e) {
        LOGE("nativeGetGuidanceHint exception: %s", e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeStartCloseSweep(
    JNIEnv* env, jobject thiz, jlong handle
) {
    if (handle == 0) return;
    try {
        snoopar_start_close_sweep(reinterpret_cast<SnoopAREngineHandle>(handle));
    } catch (const std::exception& e) {
        LOGE("nativeStartCloseSweep exception: %s", e.what());
        throwJavaException(env, e.what());
    }
}

JNIEXPORT jlong JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetObservationCount(
    JNIEnv* env, jobject thiz, jlong handle
) {
    if (handle == 0) return 0;
    try {
        return static_cast<jlong>(snoopar_get_observation_count(reinterpret_cast<SnoopAREngineHandle>(handle)));
    } catch (const std::exception& e) {
        LOGE("nativeGetObservationCount exception: %s", e.what());
        return 0;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetEstimate(
    JNIEnv* env, jobject thiz, jlong handle
) {
    if (handle == 0) return nullptr;
    try {
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
    } catch (const std::exception& e) {
        LOGE("nativeGetEstimate exception: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_org_snoopar_app_engine_SnoopArNativeEngine_nativeGetVoxelCloud(
    JNIEnv* env, jobject thiz, jlong handle, jint max_count
) {
    if (handle == 0 || max_count <= 0) return nullptr;

    try {
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
    } catch (const std::exception& e) {
        LOGE("nativeGetVoxelCloud exception: %s", e.what());
        return nullptr;
    }
}

} // extern "C"
