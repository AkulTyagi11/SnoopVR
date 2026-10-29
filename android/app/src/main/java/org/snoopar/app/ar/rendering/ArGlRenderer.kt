package org.snoopar.app.ar.rendering

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.google.ar.core.TrackingState
import org.snoopar.app.ar.ArCoreController
import org.snoopar.app.engine.AppSearchState
import org.snoopar.app.engine.VoxelPoint
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArGlRenderer(
    private val arCoreController: ArCoreController,
    private val getDisplayRotation: () -> Int = { Surface.ROTATION_0 }
) : GLSurfaceView.Renderer {

    val backgroundRenderer = BackgroundRenderer()
    val breadcrumbRenderer = BreadcrumbRenderer()
    val voxelCloudRenderer = VoxelCloudRenderer()
    val targetReticleRenderer = TargetReticleRenderer()

    private var cameraTextureInitialized = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var viewportChanged = false

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            backgroundRenderer.init()
            breadcrumbRenderer.init()
            voxelCloudRenderer.init()
            targetReticleRenderer.init()

            // Bind camera texture to ARCore session if session already created
            arCoreController.session?.let { session ->
                if (backgroundRenderer.textureId != -1) {
                    session.setCameraTextureName(backgroundRenderer.textureId)
                    cameraTextureInitialized = true
                }
            }
        } catch (_: Exception) {}
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
        try {
            arCoreController.session?.setDisplayGeometry(getDisplayRotation(), width, height)
        } catch (_: Exception) {}
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            val session = arCoreController.session ?: return

            // Lazy initialize camera texture name if session was created after surface
            if (!cameraTextureInitialized && backgroundRenderer.textureId != -1) {
                try {
                    session.setCameraTextureName(backgroundRenderer.textureId)
                    cameraTextureInitialized = true
                } catch (_: Exception) {
                    return
                }
            }

            if (viewportChanged && viewportWidth > 0 && viewportHeight > 0) {
                try {
                    session.setDisplayGeometry(getDisplayRotation(), viewportWidth, viewportHeight)
                    viewportChanged = false
                } catch (_: Exception) {}
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

            // 6. Draw 3D Bold Thermal Target Reticle & Outline Box
            targetReticleRenderer.draw(mvpMatrix)
        } catch (_: Exception) {
            // Protect GL thread from any uncaught rendering errors
        }
    }

    fun updateVoxelCloud(voxels: List<VoxelPoint>) {
        voxelCloudRenderer.updateVoxels(voxels)
    }

    fun updateTarget(
        x: Float?, y: Float?, z: Float?,
        radius: Float?,
        state: AppSearchState,
        planeAvailable: Boolean = false,
        planeY: Float = 0.0f
    ) {
        targetReticleRenderer.updateTarget(x, y, z, radius, state, planeAvailable, planeY)
    }
}
