package org.snoopar.app.ar.rendering

import android.opengl.GLES20
import org.snoopar.app.engine.VoxelPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class VoxelCloudRenderer {

    private val vertexShaderCode = """
        uniform mat4 uMvpMatrix;
        attribute vec4 aPosition; // [x, y, z, prob]
        varying float vProb;
        void main() {
            vProb = aPosition.w;
            gl_Position = uMvpMatrix * vec4(aPosition.xyz, 1.0);
            // Perspective point attenuation: nearby voxels expand into rich thermal heat blooms
            float depth = max(gl_Position.w, 0.2);
            gl_PointSize = clamp((380.0 * vProb) / depth, 24.0, 190.0);
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying float vProb;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord);
            if (dist > 0.5) {
                discard;
            }
            // Smooth Gaussian thermal radial attenuation
            float radialHeat = exp(-dist * dist * 9.0) * vProb;
            float alpha = clamp(radialHeat * 0.95, 0.0, 1.0);

            // FLIR / Ironbow false-color thermal palette
            vec3 color;
            if (vProb > 0.80) {
                // White-hot core (extreme thermal density)
                float t = (vProb - 0.80) / 0.20;
                color = mix(vec3(1.0, 0.75, 0.1), vec3(1.0, 1.0, 0.95), t);
            } else if (vProb > 0.50) {
                // Scorching Flame Orange to Vivid Amber
                float t = (vProb - 0.50) / 0.30;
                color = mix(vec3(0.95, 0.15, 0.25), vec3(1.0, 0.75, 0.1), t);
            } else if (vProb > 0.25) {
                // Infrared Crimson / Magenta
                float t = (vProb - 0.25) / 0.25;
                color = mix(vec3(0.45, 0.05, 0.75), vec3(0.95, 0.15, 0.25), t);
            } else {
                // Cool fringe (Deep Thermal Violet / Cyan)
                float t = vProb / 0.25;
                color = mix(vec3(0.05, 0.25, 0.65), vec3(0.45, 0.05, 0.75), t);
            }

            gl_FragColor = vec4(color, alpha);
        }
    """.trimIndent()

    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0

    private var vertexBuffer: FloatBuffer? = null
    private var pointCount = 0

    fun init() {
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix")
    }

    fun updateVoxels(voxels: List<VoxelPoint>) {
        if (voxels.isEmpty()) {
            synchronized(this) {
                pointCount = 0
                vertexBuffer = null
            }
            return
        }
        val count = voxels.size
        val floatArray = FloatArray(count * 4)
        for (i in voxels.indices) {
            val v = voxels[i]
            floatArray[i * 4 + 0] = v.x
            floatArray[i * 4 + 1] = v.y
            floatArray[i * 4 + 2] = v.z
            floatArray[i * 4 + 3] = v.probability
        }

        val buffer = ByteBuffer.allocateDirect(floatArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(floatArray)
                position(0)
            }

        synchronized(this) {
            vertexBuffer = buffer
            pointCount = count
        }
    }

    fun draw(mvpMatrix: FloatArray) {
        if (program == 0 || positionHandle < 0) return

        val (buffer, count) = synchronized(this) {
            val buf = vertexBuffer ?: return
            val cnt = pointCount
            if (cnt == 0) return
            Pair(buf, cnt)
        }

        try {
            buffer.position(0)
            GLES20.glUseProgram(program)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE) // Additive thermal blending
            GLES20.glDepthMask(false)

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 4, GLES20.GL_FLOAT, false, 4 * 4, buffer)

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDepthMask(true)
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
