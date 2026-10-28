package org.snoopar.app.ar

import android.content.Context
import android.os.SystemClock
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentLinkedDeque

data class CameraPoseSample(
    val timestampNs: Long,
    val x: Float, val y: Float, val z: Float,
    val gazeX: Float, val gazeY: Float, val gazeZ: Float
)

data class DetectedPlane(
    val centerX: Float, val centerY: Float, val centerZ: Float,
    val normalX: Float, val normalY: Float, val normalZ: Float,
    val distanceToCamera: Float
)

class ArCoreController(private val context: Context) {

    var session: Session? = null
        private set

    private val poseHistory = ConcurrentLinkedDeque<CameraPoseSample>()
    private val maxHistoryNanos = 2_000_000_000L // 2.0 seconds history

    var currentPose: CameraPoseSample? = null
        private set

    fun initialize(): Boolean {
        if (session != null) return true

        return try {
            val arSession = Session(context)
            val config = Config(arSession).apply {
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            }
            arSession.configure(config)
            session = arSession
            true
        } catch (_: Exception) {
            false
        }
    }

    fun onFrame(frame: Frame) {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return

        val pose = camera.pose
        val translation = pose.translation // [x, y, z]

        // Forward gaze vector in ARCore camera coordinates (camera looks down its local -Z axis)
        val zAxis = pose.zAxis
        val gazeX = -zAxis[0]
        val gazeY = -zAxis[1]
        val gazeZ = -zAxis[2]

        val timestampNs = if (frame.timestamp > 0) frame.timestamp else SystemClock.elapsedRealtimeNanos()
        val sample = CameraPoseSample(
            timestampNs = timestampNs,
            x = translation[0], y = translation[1], z = translation[2],
            gazeX = gazeX, gazeY = gazeY, gazeZ = gazeZ
        )
        currentPose = sample

        poseHistory.addLast(sample)

        // Prune older than 2 seconds
        while (poseHistory.isNotEmpty() && (timestampNs - poseHistory.first.timestampNs) > maxHistoryNanos) {
            poseHistory.pollFirst()
        }
    }

    /**
     * Interpolates the camera pose at the exact microsecond timestamp of BLE packet arrival.
     */
    fun interpolatePoseAt(timestampNs: Long): CameraPoseSample? {
        val history = poseHistory.toList()
        if (history.isEmpty()) return currentPose
        if (history.size == 1) return history[0]

        // Find nearest or bracket
        for (i in 0 until history.size - 1) {
            val p0 = history[i]
            val p1 = history[i + 1]
            if (timestampNs in p0.timestampNs..p1.timestampNs) {
                val alpha = (timestampNs - p0.timestampNs).toFloat() / (p1.timestampNs - p0.timestampNs).toFloat()
                return CameraPoseSample(
                    timestampNs = timestampNs,
                    x = p0.x + alpha * (p1.x - p0.x),
                    y = p0.y + alpha * (p1.y - p0.y),
                    z = p0.z + alpha * (p1.z - p0.z),
                    gazeX = p0.gazeX + alpha * (p1.gazeX - p0.gazeX),
                    gazeY = p0.gazeY + alpha * (p1.gazeY - p0.gazeY),
                    gazeZ = p0.gazeZ + alpha * (p1.gazeZ - p0.gazeZ)
                )
            }
        }
        return history.last()
    }

    /**
     * Finds the nearest tracked physical surface plane (table, floor, or vertical wall/surface)
     * to constrain 3D Bayesian occupancy estimates during close sweep mode.
     */
    fun getNearestPlane(): DetectedPlane? {
        val arSession = session ?: return null
        val curPose = currentPose ?: return null

        var nearest: DetectedPlane? = null
        var minDist = Float.MAX_VALUE

        try {
            for (plane in arSession.getAllTrackables(com.google.ar.core.Plane::class.java)) {
                if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) continue

                val centerPose = plane.centerPose
                val px = centerPose.tx()
                val py = centerPose.ty()
                val pz = centerPose.tz()

                // In ARCore, the plane normal is defined by the pose's Y axis
                val yAxis = centerPose.yAxis
                val nx = yAxis[0]
                val ny = yAxis[1]
                val nz = yAxis[2]

                val dx = px - curPose.x
                val dy = py - curPose.y
                val dz = pz - curPose.z
                val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)

                if (dist < minDist) {
                    minDist = dist
                    nearest = DetectedPlane(px, py, pz, nx, ny, nz, dist)
                }
            }
        } catch (_: Exception) {}

        return nearest
    }

    fun resume() {
        try { session?.resume() } catch (_: Exception) {}
    }

    fun pause() {
        try { session?.pause() } catch (_: Exception) {}
    }

    fun destroy() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        poseHistory.clear()
    }
}
