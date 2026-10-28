package org.snoopar.logger.ar

import android.content.Context
import android.os.SystemClock
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

data class ArPoseEvent(
    val timestampNs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val qw: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val trackingState: String
)

class ArPoseRecorder(
    private val context: Context,
    private val onPoseAvailable: (ArPoseEvent) -> Unit
) {
    var session: Session? = null
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

    fun processFrame(frame: Frame) {
        val camera = frame.camera
        val stateStr = when (camera.trackingState) {
            TrackingState.TRACKING -> "TRACKING"
            TrackingState.PAUSED -> "PAUSED"
            TrackingState.STOPPED -> "STOPPED"
            null -> "UNKNOWN"
        }

        val pose = camera.pose
        val translation = pose.translation // [x, y, z]
        val rotation = pose.rotationQuaternion // [x, y, z, w]

        val event = ArPoseEvent(
            timestampNs = SystemClock.elapsedRealtimeNanos(),
            x = translation[0],
            y = translation[1],
            z = translation[2],
            qw = rotation[3],
            qx = rotation[0],
            qy = rotation[1],
            qz = rotation[2],
            trackingState = stateStr
        )
        onPoseAvailable(event)
    }

    fun pause() {
        try {
            session?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            session?.resume()
        } catch (_: Exception) {}
    }

    fun destroy() {
        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
    }
}
