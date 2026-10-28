package org.snoopar.app.ar.rendering

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.TrackingState
import org.snoopar.app.ar.ArCoreController
import org.snoopar.app.engine.VoxelPoint
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArGlRenderer(
    private val arCoreController: ArCoreController
) : GLSurfaceView.Renderer {

    val backgroundRenderer = BackgroundRenderer()
    val breadcrumbRenderer = BreadcrumbRenderer()
    val voxelCloudRenderer = VoxelCloudRenderer()

    private var cameraTextureInitialized = false

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        backgroundRenderer.init()
        breadcrumbRenderer.init()
        voxelCloudRenderer.init()

        // Bind camera texture to ARCore session if session already created
        arCoreController.session?.let { session ->
            session.setCameraTextureName(backgroundRenderer.textureId)
            cameraTextureInitialized = true
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        arCoreController.session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = arCoreController.session ?: return

        // Lazy initialize camera texture name if session was created after surface
        if (!cameraTextureInitialized && backgroundRenderer.textureId != -1) {
            session.setCameraTextureName(backgroundRenderer.textureId)
            cameraTextureInitialized = true
        }

        val frame = try {
            session.update()
        } catch (_: Exception) {
            return
        }

        // 1. Render camera background quad first
        backgroundRenderer.draw(frame)

        // 2. Feed frame pose to controller
        arCoreController.onFrame(frame)

        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return

        // 3. Compute 3D Camera Projection * View Matrix
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // 4. Draw 3D Breadcrumb nodes
        breadcrumbRenderer.draw(mvpMatrix)

        // 5. Draw 3D Volumetric Probability Cloud
        voxelCloudRenderer.draw(mvpMatrix)
    }

    fun updateVoxelCloud(voxels: List<VoxelPoint>) {
        voxelCloudRenderer.updateVoxels(voxels)
    }
}
