package org.snoopar.app.ar.rendering

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class Breadcrumb(val x: Float, val y: Float, val z: Float, val rssi: Float)

class BreadcrumbRenderer {

    private val vertexShaderCode = """
        uniform mat4 uMvpMatrix;
        attribute vec4 aPosition; // [x, y, z, normalizedRssi]
        varying float vNormRssi;
        void main() {
            vNormRssi = aPosition.w;
            gl_Position = uMvpMatrix * vec4(aPosition.xyz, 1.0);
            gl_PointSize = 10.0;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying float vNormRssi;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            if (length(coord) > 0.5) discard;

            // Color gradient: Blue (-85 dBm) -> Yellow (-72 dBm) -> Red (-60 dBm)
            vec3 color;
            if (vNormRssi < 0.5) {
                color = mix(vec3(0.2, 0.5, 1.0), vec3(1.0, 0.9, 0.2), vNormRssi * 2.0);
            } else {
                color = mix(vec3(1.0, 0.9, 0.2), vec3(1.0, 0.2, 0.2), (vNormRssi - 0.5) * 2.0);
            }
            gl_FragColor = vec4(color, 0.9);
        }
    """.trimIndent()

    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0

    private val breadcrumbs = mutableListOf<Breadcrumb>()
    private var vertexBuffer: FloatBuffer? = null

    fun init() {
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix")
    }

    companion object {
        private const val MAX_BREADCRUMBS = 250
    }

    fun addBreadcrumb(x: Float, y: Float, z: Float, rssi: Float) {
        synchronized(breadcrumbs) {
            breadcrumbs.add(Breadcrumb(x, y, z, rssi))
            while (breadcrumbs.size > MAX_BREADCRUMBS) {
                breadcrumbs.removeAt(0)
            }
            updateBuffer()
        }
    }

    fun clear() {
        synchronized(breadcrumbs) {
            breadcrumbs.clear()
            vertexBuffer = null
        }
    }

    private fun updateBuffer() {
        val floatArray = FloatArray(breadcrumbs.size * 4)
        for (i in breadcrumbs.indices) {
            val b = breadcrumbs[i]
            // Normalize RSSI from [-85, -55] to [0.0, 1.0]
            val normRssi = ((b.rssi - (-85.0f)) / 30.0f).coerceIn(0.0f, 1.0f)
            floatArray[i * 4 + 0] = b.x
            floatArray[i * 4 + 1] = b.y
            floatArray[i * 4 + 2] = b.z
            floatArray[i * 4 + 3] = normRssi
        }

        vertexBuffer = ByteBuffer.allocateDirect(floatArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(floatArray)
                position(0)
            }
    }

    fun draw(mvpMatrix: FloatArray) {
        if (program == 0 || positionHandle < 0) return

        val (buffer, count) = synchronized(breadcrumbs) {
            val buf = vertexBuffer ?: return
            val cnt = breadcrumbs.size
            if (cnt == 0) return
            Pair(buf, cnt)
        }

        try {
            buffer.position(0)
            GLES20.glUseProgram(program)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 4, GLES20.GL_FLOAT, false, 4 * 4, buffer)

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisable(GLES20.GL_BLEND)
        } catch (_: Exception) {}
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
