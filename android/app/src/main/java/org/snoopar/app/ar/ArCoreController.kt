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

class ArCoreController(private val context: Context) {

    var session: Session? = null
        private set

    private val poseHistory = ConcurrentLinkedDeque<CameraPoseSample>()
    private val maxHistoryNanos = 2_000_000_000L // 2.0 seconds history

    var currentPose: CameraPoseSample? = null
        private set

    fun initialize(): Boolean {
        return try {
            val arSession = Session(context)
            val config = Config(arSession).apply {
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
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

        // Forward gaze vector in ARCore camera coordinates
        val forwardVector = pose.transformedCoordinateSystem[2] // Z axis
        val gazeX = -forwardVector[0]
        val gazeY = -forwardVector[1]
        val gazeZ = -forwardVector[2]

        val nowNs = SystemClock.elapsedRealtimeNanos()
        val sample = CameraPoseSample(
            timestampNs = nowNs,
            x = translation[0], y = translation[1], z = translation[2],
            gazeX = gazeX, gazeY = gazeY, gazeZ = gazeZ
        )
        currentPose = sample

        poseHistory.addLast(sample)

        // Prune older than 2 seconds
        while (poseHistory.isNotEmpty() && (nowNs - poseHistory.first.timestampNs) > maxHistoryNanos) {
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
