package org.snoopar.app.ar.rendering

import android.opengl.GLES20
import android.os.SystemClock
import org.snoopar.app.engine.AppSearchState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-visibility AR holographic reticle and FLIR-style thermal target outline.
 * Renders bold 3D corner brackets, rotating thermal scan rings, and surface anchor pins
 * directly over the pinpointed physical tracking tag.
 */
class TargetReticleRenderer {

    private val vertexShaderCode = """
        uniform mat4 uMvpMatrix;
        attribute vec3 aPosition;
        void main() {
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 uColor;
        void main() {
            gl_FragColor = uColor;
        }
    """.trimIndent()

    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0

    // Target geometry state
    private var targetX = 0.0f
    private var targetY = 0.0f
    private var targetZ = 0.0f
    private var confidenceRadius = 0.35f
    private var searchState = AppSearchState.COARSE_SEARCH
    private var hasTarget = false
    private var hasSurfacePlane = false
    private var surfacePlaneY = 0.0f

    // Vertex Buffers
    private var boxVertexBuffer: FloatBuffer? = null
    private var boxVertexCount = 0

    private var ringVertexBuffer: FloatBuffer? = null
    private var ringVertexCount = 0

    private var pinVertexBuffer: FloatBuffer? = null
    private var pinVertexCount = 0

    private val startTimeMs = SystemClock.uptimeMillis()

    fun init() {
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }

    fun updateTarget(
        x: Float?, y: Float?, z: Float?,
        radius: Float?,
        state: AppSearchState,
        planeAvailable: Boolean = false,
        planeY: Float = 0.0f
    ) {
        if (x == null || y == null || z == null) {
            hasTarget = false
            return
        }

        hasTarget = true
        targetX = x
        targetY = y
        targetZ = z
        confidenceRadius = radius?.coerceIn(0.04f, 1.20f) ?: 0.30f
        searchState = state
        hasSurfacePlane = planeAvailable
        surfacePlaneY = planeY

        rebuildGeometry()
    }

    private fun rebuildGeometry() {
        // 1. Build 3D Corner Brackets Box surrounding target
        // Tag physical size: AirTag is ~3.2cm; bounding box adapts to confidence radius
        val halfSize = when (searchState) {
            AppSearchState.TARGET_LOCKED -> 0.06f
            AppSearchState.CLOSE_SWEEP_ACTIVE -> (confidenceRadius * 0.45f).coerceIn(0.07f, 0.16f)
            else -> (confidenceRadius * 0.55f).coerceIn(0.12f, 0.32f)
        }
        val armLen = halfSize * 0.50f

        val boxLines = mutableListOf<Float>()

        // 8 Corners of cube [-halfSize, +halfSize]
        for (sx in listOf(-1.0f, 1.0f)) {
            for (sy in listOf(-1.0f, 1.0f)) {
                for (sz in listOf(-1.0f, 1.0f)) {
                    val cx = targetX + sx * halfSize
                    val cy = targetY + sy * halfSize
                    val cz = targetZ + sz * halfSize

                    // Branch along X
                    boxLines.addAll(listOf(cx, cy, cz, cx - sx * armLen, cy, cz))
                    // Branch along Y
                    boxLines.addAll(listOf(cx, cy, cz, cx, cy - sy * armLen, cz))
                    // Branch along Z
                    boxLines.addAll(listOf(cx, cy, cz, cx, cy, cz - sz * armLen))
                }
            }
        }

        // Inner AirTag Disc Crosshair (3.2cm circle in horizontal plane)
        val discRadius = 0.024f
        val discSegments = 24
        for (i in 0 until discSegments) {
            val theta1 = (i.toFloat() / discSegments) * (2.0f * Math.PI.toFloat())
            val theta2 = ((i + 1).toFloat() / discSegments) * (2.0f * Math.PI.toFloat())
            boxLines.addAll(listOf(
                targetX + discRadius * cos(theta1), targetY, targetZ + discRadius * sin(theta1),
                targetX + discRadius * cos(theta2), targetY, targetZ + discRadius * sin(theta2)
            ))
        }
        // Center pip crosshair '+'
        val crossArm = 0.015f
        boxLines.addAll(listOf(
            targetX - crossArm, targetY, targetZ, targetX + crossArm, targetY, targetZ,
            targetX, targetY, targetZ - crossArm, targetX, targetY, targetZ + crossArm
        ))

        boxVertexCount = boxLines.size / 3
        boxVertexBuffer = ByteBuffer.allocateDirect(boxLines.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(boxLines.toFloatArray())
                position(0)
            }

        // 2. Build Pulsing Outer Thermal Radar Rings
        val ringLines = mutableListOf<Float>()
        val ringSegments = 36
        val ringR = halfSize * 1.45f
        for (i in 0 until ringSegments) {
            val theta1 = (i.toFloat() / ringSegments) * (2.0f * Math.PI.toFloat())
            val theta2 = ((i + 1).toFloat() / ringSegments) * (2.0f * Math.PI.toFloat())
            ringLines.addAll(listOf(
                targetX + ringR * cos(theta1), targetY, targetZ + ringR * sin(theta1),
                targetX + ringR * cos(theta2), targetY, targetZ + ringR * sin(theta2)
            ))
        }
        // 4 outer tick marks at 90 deg intervals
        val tickInner = ringR * 0.95f
        val tickOuter = ringR * 1.30f
        for (k in 0..3) {
            val a = (k.toFloat() * Math.PI.toFloat() * 0.5f)
            ringLines.addAll(listOf(
                targetX + tickInner * cos(a), targetY, targetZ + tickInner * sin(a),
                targetX + tickOuter * cos(a), targetY, targetZ + tickOuter * sin(a)
            ))
        }

        ringVertexCount = ringLines.size / 3
        ringVertexBuffer = ByteBuffer.allocateDirect(ringLines.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(ringLines.toFloatArray())
                position(0)
            }

        // 3. Build Vertical Surface Beacon Pin
        val groundY = if (hasSurfacePlane) surfacePlaneY else (targetY - halfSize * 1.6f)
        val pinLines = mutableListOf(
            targetX, targetY, targetZ,
            targetX, groundY, targetZ
        )
        // Surface anchor cross 'X'
        val pinArm = 0.05f
        pinLines.addAll(listOf(
            targetX - pinArm, groundY, targetZ - pinArm, targetX + pinArm, groundY, targetZ + pinArm,
            targetX - pinArm, groundY, targetZ + pinArm, targetX + pinArm, groundY, targetZ - pinArm
        ))

        pinVertexCount = pinLines.size / 3
        pinVertexBuffer = ByteBuffer.allocateDirect(pinLines.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(pinLines.toFloatArray())
                position(0)
            }
    }

    fun draw(mvpMatrix: FloatArray) {
        if (!hasTarget || program == 0 || positionHandle < 0) return

        val boxBuf = boxVertexBuffer ?: return
        val ringBuf = ringVertexBuffer ?: return
        val pinBuf = pinVertexBuffer ?: return

        try {
            val elapsedSec = (SystemClock.uptimeMillis() - startTimeMs) / 1000.0f
            val pulse = (sin(elapsedSec * 4.5f) * 0.5f + 0.5f) // [0.0, 1.0]

            GLES20.glUseProgram(program)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE) // Additive neon glow
            GLES20.glDisable(GLES20.GL_DEPTH_TEST) // Draw on top of camera & scene

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glEnableVertexAttribArray(positionHandle)

            // Select High-Contrast Thermal Palette based on Stage
            val (r, g, b) = when (searchState) {
                AppSearchState.TARGET_LOCKED -> Triple(0.0f, 1.0f, 0.45f) // Emerald Lock: #00E676
                AppSearchState.CLOSE_SWEEP_ACTIVE -> Triple(1.0f, 0.42f, 0.0f) // Thermal Flame: #FF6D00
                else -> Triple(0.0f, 0.90f, 1.0f) // Thermal Cyan: #00E5FF
            }

            // 1. Draw Bold 3D Bounding Box Corner Brackets
            boxBuf.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, boxBuf)
            val boxAlpha = 0.85f + 0.15f * pulse
            GLES20.glUniform4f(colorHandle, r, g, b, boxAlpha)
            GLES20.glLineWidth(5.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, boxVertexCount)

            // 2. Draw Pulsing Outer Thermal Scan Ring
            ringBuf.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, ringBuf)
            val ringAlpha = 0.40f + 0.45f * pulse
            GLES20.glUniform4f(colorHandle, r * 0.9f, g * 0.9f, b * 0.9f, ringAlpha)
            GLES20.glLineWidth(3.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, ringVertexCount)

            // 3. Draw Vertical Ground Beacon Pin
            pinBuf.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, pinBuf)
            val pinAlpha = 0.50f + 0.35f * (1.0f - pulse)
            GLES20.glUniform4f(colorHandle, 1.0f, 1.0f, 1.0f, pinAlpha)
            GLES20.glLineWidth(2.5f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, pinVertexCount)

            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_BLEND)
        } catch (e: Exception) {
            android.util.Log.w("TargetReticleRenderer", "Reticle draw error caught", e)
        }
    }

    private fun createProgram(vCode: String, fCode: String): Int {
        val vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
            GLES20.glShaderSource(it, vCode)
            GLES20.glCompileShader(it)
        }
        val fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
            GLES20.glShaderSource(it, fCode)
            GLES20.glCompileShader(it)
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }
    }
}
