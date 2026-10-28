package org.snoopar.app.ar.rendering

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders the Android camera background using ARCore's external OES texture.
 * Ensures the live physical camera feed is displayed behind AR voxel clouds and breadcrumbs.
 */
class BackgroundRenderer {

    var textureId: Int = -1
        private set

    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var textureUniform = 0

    private val quadCoords: FloatBuffer
    private val quadTexCoords: FloatBuffer
    private val transformedTexCoords: FloatBuffer

    companion object {
        private const val COORDS_PER_VERTEX = 2
        private const val TEXCOORDS_PER_VERTEX = 2

        // Normalized Device Coordinates (NDC) covering the full screen [-1, 1]
        private val QUAD_COORDS_ARRAY = floatArrayOf(
            -1.0f, -1.0f,
            +1.0f, -1.0f,
            -1.0f, +1.0f,
            +1.0f, +1.0f
        )

        // Default UV coordinates
        private val QUAD_TEXCOORDS_ARRAY = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )

        private const val VERTEX_SHADER_CODE = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
    }

    init {
        quadCoords = ByteBuffer.allocateDirect(QUAD_COORDS_ARRAY.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(QUAD_COORDS_ARRAY)
                position(0)
            }

        quadTexCoords = ByteBuffer.allocateDirect(QUAD_TEXCOORDS_ARRAY.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(QUAD_TEXCOORDS_ARRAY)
                position(0)
            }

        transformedTexCoords = ByteBuffer.allocateDirect(QUAD_TEXCOORDS_ARRAY.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    fun init() {
        // Generate external OES texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Compile shaders
        val vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
            GLES20.glShaderSource(it, VERTEX_SHADER_CODE.trimIndent())
            GLES20.glCompileShader(it)
        }
        val fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
            GLES20.glShaderSource(it, FRAGMENT_SHADER_CODE.trimIndent())
            GLES20.glCompileShader(it)
        }

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }

        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
    }

    /**
     * Draws the camera background quad.
     */
    fun draw(frame: Frame) {
        if (textureId == -1 || program == 0) return

        // Transform display UV coordinates to match aspect ratio and camera orientation
        frame.transformDisplayUvCoords(quadTexCoords, transformedTexCoords)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        // Quad positions
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(
            positionAttrib,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadCoords
        )

        // Transformed UV coordinates
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glVertexAttribPointer(
            texCoordAttrib,
            TEXCOORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            transformedTexCoords
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)

        // Restore OpenGL state for subsequent 3D render passes
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }
}
