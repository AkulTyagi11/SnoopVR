package org.snoopar.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.snoopar.app.ar.ArCoreController
import org.snoopar.app.ar.rendering.ArGlRenderer
import org.snoopar.app.ble.DiscoveredTracker
import org.snoopar.app.ble.TrackerScanner
import org.snoopar.app.engine.AppGuidanceHint
import org.snoopar.app.engine.AppSearchState
import org.snoopar.app.engine.SnoopArNativeEngine
import org.snoopar.app.haptics.GeigerHapticFeedback
import org.snoopar.app.ui.SnoopArScreen
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var nativeEngine: SnoopArNativeEngine
    private lateinit var arCoreController: ArCoreController
    private lateinit var glRenderer: ArGlRenderer
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var geigerHaptics: GeigerHapticFeedback
    private var trackerScanner: TrackerScanner? = null
    private var userRequestedInstall = true

    // UI Reactive State
    private val searchState = mutableStateOf(AppSearchState.COARSE_SEARCH)
    private val guidanceHint = mutableStateOf(AppGuidanceHint.CONTINUE_SWEEP)
    private val observationCount = mutableLongStateOf(0L)
    private val latestRssi = mutableFloatStateOf(-100.0f)
    private val targetName = mutableStateOf("Scanning for trackers...")
    private val confidenceRadius = mutableStateOf<Float?>(null)
    private val selectedAddress = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startTrackingPipelines()
        } else {
            Toast.makeText(this, "Camera & Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nativeEngine = SnoopArNativeEngine(0) // AirTag default
        arCoreController = ArCoreController(this)
        glRenderer = ArGlRenderer(arCoreController)
        geigerHaptics = GeigerHapticFeedback(this)

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(glRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        trackerScanner = TrackerScanner(bluetoothAdapter) { address, rssi, timestampNs ->
            onBlePacketReceived(address, rssi, timestampNs)
        }

        checkAndRequestPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val trackers = trackerScanner?.discoveredTrackers?.collectAsState()?.value ?: emptyList()

                SnoopArScreen(
                    glSurfaceView = glSurfaceView,
                    searchState = searchState.value,
                    guidanceHint = guidanceHint.value,
                    observationCount = observationCount.longValue,
                    latestRssi = latestRssi.floatValue,
                    targetName = targetName.value,
                    confidenceRadius = confidenceRadius.value,
                    trackers = trackers,
                    selectedAddress = selectedAddress.value,
                    onSelectTracker = { tracker -> onSelectTargetTracker(tracker) },
                    onStartCloseSweep = {
                        nativeEngine.startCloseSweep()
                        searchState.value = AppSearchState.CLOSE_SWEEP_ACTIVE
                        guidanceHint.value = AppGuidanceHint.APPROACH_SURFACE
                    },
                    onReset = {
                        nativeEngine.reset()
                        glRenderer.breadcrumbRenderer.clear()
                        glRenderer.updateVoxelCloud(emptyList())
                        observationCount.longValue = 0L
                        searchState.value = AppSearchState.COARSE_SEARCH
                        guidanceHint.value = AppGuidanceHint.CONTINUE_SWEEP
                        confidenceRadius.value = null
                    }
                )
            }
        }
    }

    private fun onBlePacketReceived(address: String, rssi: Float, timestampNs: Long) {
        val pose = arCoreController.interpolatePoseAt(timestampNs) ?: return

        lifecycleScope.launch(Dispatchers.Default) {
            // Ingest into C++20 Core Engine via JNI on worker thread
            nativeEngine.addObservation(
                pose.x, pose.y, pose.z,
                pose.gazeX, pose.gazeY, pose.gazeZ,
                rssi, timestampNs
            )

            // Add 3D AR Breadcrumb
            glRenderer.breadcrumbRenderer.addBreadcrumb(pose.x, pose.y, pose.z, rssi)

            // Query updated 3D Volumetric Probability Cloud
            val voxels = nativeEngine.getVoxelCloud(maxCount = 80)
            glRenderer.updateVoxelCloud(voxels)

            val count = nativeEngine.getObservationCount()
            val state = nativeEngine.getSearchState()
            val hint = nativeEngine.getGuidanceHint()
            val estimate = nativeEngine.getEstimate()

            withContext(Dispatchers.Main) {
                observationCount.longValue = count
                latestRssi.floatValue = rssi
                searchState.value = state
                guidanceHint.value = hint

                if (estimate != null) {
                    confidenceRadius.value = estimate.confidenceRadius

                    // Compute Geiger alignment dot product
                    val dx = estimate.x - pose.x
                    val dy = estimate.y - pose.y
                    val dz = estimate.z - pose.z
                    val dist = sqrt(dx * dx + dy * dy + dz * dz)
                    if (dist > 1e-4f) {
                        val dot = (pose.gazeX * (dx / dist)) + (pose.gazeY * (dy / dist)) + (pose.gazeZ * (dz / dist))
                        geigerHaptics.update(dot, dist)
                    }
                }
            }
        }
    }

    private fun onSelectTargetTracker(tracker: DiscoveredTracker) {
        selectedAddress.value = tracker.deviceAddress
        targetName.value = "${tracker.family.displayName} (${tracker.deviceAddress.takeLast(5)})"
        nativeEngine.setTrackerType(tracker.family.trackerTypeId)
        trackerScanner?.setTargetDevice(tracker.deviceAddress)

        // Reset search pipeline for new target
        nativeEngine.reset()
        glRenderer.breadcrumbRenderer.clear()
        glRenderer.updateVoxelCloud(emptyList())
        observationCount.longValue = 0L
        searchState.value = AppSearchState.COARSE_SEARCH
        guidanceHint.value = AppGuidanceHint.CONTINUE_SWEEP
        confidenceRadius.value = null
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            startTrackingPipelines()
        }
    }

    private fun startTrackingPipelines() {
        checkArCoreInstallAndResume()
    }

    private fun checkArCoreInstallAndResume() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    userRequestedInstall = false
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    if (arCoreController.initialize()) {
                        arCoreController.resume()
                        glSurfaceView.onResume()
                        trackerScanner?.start()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "ARCore initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            checkArCoreInstallAndResume()
        }
    }

    override fun onPause() {
        super.onPause()
        trackerScanner?.stop()
        glSurfaceView.onPause()
        arCoreController.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        trackerScanner?.stop()
        glSurfaceView.onPause()
        arCoreController.destroy()
        nativeEngine.close()
    }
}
