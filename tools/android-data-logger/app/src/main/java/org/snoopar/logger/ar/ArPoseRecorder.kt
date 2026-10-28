package org.snoopar.logger.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.MotionEvent
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class ArPoseEvent(
    val frameTimestampNs: Long,
    val clockTimestampNs: Long,
    val x: Float, val y: Float, val z: Float,
    val qw: Float, val qx: Float, val qy: Float, val qz: Float,
    val trackingState: String
)

class ArPoseRecorder(
    private val context: Context,
    private val onPoseAvailable: (ArPoseEvent) -> Unit,
    private val onGroundTruthTapped: (Float, Float, Float) -> Unit
) : GLSurfaceView.Renderer {

    var session: Session? = null
        private set

    private var cameraTextureId = -1
    private var pendingTap: MotionEvent? = null

    // Background shader
    private var quadProgram = 0
    private var quadPositionParam = 0
    private var quadTexCoordParam = 0

    fun initialize(): Boolean {
        if (session != null) return true
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

    fun onTouch(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP) {
            pendingTap = MotionEvent.obtain(event)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Generate camera texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

        session?.setCameraTextureName(cameraTextureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val s = session ?: return
        val frame = try {
            s.update()
        } catch (_: Exception) {
            return
        }

        // Process ground-truth hit test if tapped
        pendingTap?.let { tap ->
            val hits = frame.hitTest(tap.x, tap.y)
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable is Plane || trackable is Point) {
                    val hitPose = hit.hitPose
                    onGroundTruthTapped(hitPose.tx(), hitPose.ty(), hitPose.tz())
                    break
                }
            }
            pendingTap = null
        }

        // Process camera pose
        val camera = frame.camera
        val stateStr = when (camera.trackingState) {
            TrackingState.TRACKING -> "TRACKING"
            TrackingState.PAUSED -> "PAUSED"
            TrackingState.STOPPED -> "STOPPED"
            null -> "UNKNOWN"
        }

        val pose = camera.pose
        val translation = pose.translation
        val rotation = pose.rotationQuaternion

        val event = ArPoseEvent(
            frameTimestampNs = frame.timestamp,
            clockTimestampNs = SystemClock.elapsedRealtimeNanos(),
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

    fun resume() {
        try {
            session?.resume()
        } catch (_: Exception) {}
    }

    fun pause() {
        try {
            session?.pause()
        } catch (_: Exception) {}
    }

    fun destroy() {
        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
    }
}
