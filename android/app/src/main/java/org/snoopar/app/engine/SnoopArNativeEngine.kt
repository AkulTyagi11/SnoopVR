package org.snoopar.app.engine

data class VoxelPoint(val x: Float, val y: Float, val z: Float, val probability: Float)

data class TargetEstimate(val x: Float, val y: Float, val z: Float, val confidenceRadius: Float)

enum class AppSearchState(val id: Int) {
    COARSE_SEARCH(0),
    READY_FOR_CLOSE_SWEEP(1),
    CLOSE_SWEEP_ACTIVE(2),
    TARGET_LOCKED(3);

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: COARSE_SEARCH
    }
}

enum class AppGuidanceHint(val id: Int, val message: String) {
    CONTINUE_SWEEP(0, "Sweep around the perimeter"),
    STEP_SIDEWAYS(1, "Step 1m sideways to break collinear symmetry"),
    CHANGE_HEIGHT(2, "Vary camera height (scan higher / crouch lower)"),
    APPROACH_SURFACE(3, "Coarse zone found! Move within 30cm of surface to lock"),
    TARGET_LOCKED(4, "Target locked! Within 25cm of indicated spot");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: CONTINUE_SWEEP
    }
}

class SnoopArNativeEngine(trackerType: Int = 0) : AutoCloseable {

    companion object {
        init {
            try {
                System.loadLibrary("snoopar_jni")
            } catch (_: UnsatisfiedLinkError) {
                // Allows running in local unit test environments without native lib
            }
        }
    }

    private var handle: Long = nativeCreateEngine(trackerType)

    fun reset() {
        if (handle != 0L) nativeResetEngine(handle)
    }

    fun setTrackerType(trackerType: Int) {
        if (handle != 0L) nativeSetTrackerType(handle, trackerType)
    }

    fun addObservation(
        x: Float, y: Float, z: Float,
        gazeX: Float, gazeY: Float, gazeZ: Float,
        rssi: Float, timestampNs: Long = System.nanoTime()
    ) {
        if (handle != 0L) {
            nativeAddObservation(handle, x, y, z, gazeX, gazeY, gazeZ, rssi, timestampNs)
        }
    }

    fun startCloseSweep() {
        if (handle != 0L) nativeStartCloseSweep(handle)
    }

    fun getSearchState(): AppSearchState {
        if (handle == 0L) return AppSearchState.COARSE_SEARCH
        return AppSearchState.fromId(nativeGetSearchState(handle))
    }

    fun getGuidanceHint(): AppGuidanceHint {
        if (handle == 0L) return AppGuidanceHint.CONTINUE_SWEEP
        return AppGuidanceHint.fromId(nativeGetGuidanceHint(handle))
    }

    fun getObservationCount(): Long {
        if (handle == 0L) return 0L
        return nativeGetObservationCount(handle)
    }

    fun getEstimate(): TargetEstimate? {
        if (handle == 0L) return null
        val raw = nativeGetEstimate(handle) ?: return null
        if (raw.size < 4) return null
        return TargetEstimate(raw[0], raw[1], raw[2], raw[3])
    }

    fun getVoxelCloud(maxCount: Int = 100): List<VoxelPoint> {
        if (handle == 0L) return emptyList()
        val raw = nativeGetVoxelCloud(handle, maxCount) ?: return emptyList()
        val list = ArrayList<VoxelPoint>(raw.size / 4)
        var i = 0
        while (i < raw.size) {
            list.add(VoxelPoint(raw[i], raw[i + 1], raw[i + 2], raw[i + 3]))
            i += 4
        }
        return list
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroyEngine(handle)
            handle = 0L
        }
    }

    // Native declarations
    private external fun nativeCreateEngine(trackerType: Int): Long
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeResetEngine(handle: Long)
    private external fun nativeSetTrackerType(handle: Long, trackerType: Int)
    private external fun nativeAddObservation(
        handle: Long,
        x: Float, y: Float, z: Float,
        gazeX: Float, gazeY: Float, gazeZ: Float,
        rssi: Float, timestampNs: Long
    )
    private external fun nativeGetSearchState(handle: Long): Int
    private external fun nativeGetGuidanceHint(handle: Long): Int
    private external fun nativeStartCloseSweep(handle: Long)
    private external fun nativeGetObservationCount(handle: Long): Long
    private external fun nativeGetEstimate(handle: Long): FloatArray?
    private external fun nativeGetVoxelCloud(handle: Long, maxCount: Int): FloatArray?
}
