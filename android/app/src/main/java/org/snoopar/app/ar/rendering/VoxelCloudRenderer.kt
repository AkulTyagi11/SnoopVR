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
            gl_PointSize = max(16.0 * vProb, 6.0);
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying float vProb;
        void main() {
            // Soft circular glowing point sprite
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord);
            if (dist > 0.5) {
                discard;
            }
            float alpha = (1.0 - dist * 2.0) * vProb * 0.85;
            // Thermal glow: Red-Orange core, golden edge
            vec3 color = mix(vec3(1.0, 0.6, 0.1), vec3(1.0, 0.1, 0.05), vProb);
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
        val (buffer, count) = synchronized(this) {
            val buf = vertexBuffer ?: return
            val cnt = pointCount
            if (cnt == 0) return
            Pair(buf, cnt)
        }

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
