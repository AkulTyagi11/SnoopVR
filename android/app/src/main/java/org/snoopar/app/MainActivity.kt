package org.snoopar.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import org.snoopar.app.data.SessionRecorder
import org.snoopar.app.engine.AppGuidanceHint
import org.snoopar.app.engine.AppSearchState
import org.snoopar.app.engine.SnoopArNativeEngine
import org.snoopar.app.engine.TargetBearing
import org.snoopar.app.feedback.GeigerFeedbackManager
import org.snoopar.app.ui.SnoopArScreen
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    // Multi-Target Parallel Tracking Engines (keyed by Bluetooth device address)
    private val engineMap = mutableMapOf<String, SnoopArNativeEngine>()
    private val sessionRecorder = SessionRecorder()

    private lateinit var arCoreController: ArCoreController
    private lateinit var glRenderer: ArGlRenderer
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var geigerFeedback: GeigerFeedbackManager
    private var trackerScanner: TrackerScanner? = null
    private var userRequestedInstall = true
    private var isSessionConfigured = false

    // UI Reactive State
    private val searchState = mutableStateOf(AppSearchState.COARSE_SEARCH)
    private val guidanceHint = mutableStateOf(AppGuidanceHint.CONTINUE_SWEEP)
    private val observationCount = mutableLongStateOf(0L)
    private val latestRssi = mutableFloatStateOf(-100.0f)
    private val targetName = mutableStateOf("Scanning for trackers...")
    private val confidenceRadius = mutableStateOf<Float?>(null)
    private val targetBearing = mutableStateOf<TargetBearing?>(null)
    private val isAudioMuted = mutableStateOf(false)
    private val selectedAddress = mutableStateOf<String?>(null)
    private val isRecording = mutableStateOf(false)
    private val recordedSampleCount = mutableIntStateOf(0)
    private val hasGroundTruth = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasRequiredPermissions()) {
            startTrackingPipelines()
        } else {
            Toast.makeText(this, "Camera & Location permissions required for AR and BLE tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install defensive global exception logger
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SnoopAR", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
        }

        arCoreController = ArCoreController(this)
        glRenderer = ArGlRenderer(arCoreController) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }
        }
        geigerFeedback = GeigerFeedbackManager(this)

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
                    targetBearing = targetBearing.value,
                    isAudioMuted = isAudioMuted.value,
                    isRecording = isRecording.value,
                    recordedCount = recordedSampleCount.intValue,
                    hasGroundTruth = hasGroundTruth.value,
                    trackers = trackers,
                    selectedAddress = selectedAddress.value,
                    onSelectTracker = { tracker -> onSelectTargetTracker(tracker) },
                    onStartCloseSweep = {
                        val activeEngine = getActiveEngine()
                        activeEngine.startCloseSweep()
                        searchState.value = AppSearchState.CLOSE_SWEEP_ACTIVE
                        guidanceHint.value = AppGuidanceHint.APPROACH_SURFACE

                        // Snap to nearest detected physical surface plane if available
                        arCoreController.getNearestPlane()?.let { plane ->
                            activeEngine.setSurfacePlane(
                                plane.centerX, plane.centerY, plane.centerZ,
                                plane.normalX, plane.normalY, plane.normalZ,
                                sigmaMeters = 0.08f
                            )
                        }
                    },
                    onToggleAudioMute = {
                        isAudioMuted.value = !isAudioMuted.value
                        geigerFeedback.setAudioMuted(isAudioMuted.value)
                    },
                    onToggleRecording = {
                        if (isRecording.value) {
                            val savedPath = sessionRecorder.stopRecording(this@MainActivity)
                            isRecording.value = false
                            hasGroundTruth.value = false
                            recordedSampleCount.intValue = 0
                            if (savedPath != null) {
                                Toast.makeText(this@MainActivity, "Trace saved: ${savedPath.substringAfterLast('/')}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val addr = selectedAddress.value ?: "broadcast"
                            val tType = trackerScanner?.discoveredTrackers?.value?.find { it.deviceAddress == addr }?.family?.trackerTypeId ?: 0
                            sessionRecorder.startRecording(addr, tType)
                            isRecording.value = true
                            hasGroundTruth.value = false
                            recordedSampleCount.intValue = 0
                            Toast.makeText(this@MainActivity, "Session recording started", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMarkGroundTruth = {
                        arCoreController.currentPose?.let { pose ->
                            sessionRecorder.setGroundTruth(pose.x, pose.y, pose.z)
                            hasGroundTruth.value = true
                            Toast.makeText(this@MainActivity, "Pinned ground truth: (%.2f, %.2f, %.2f)".format(pose.x, pose.y, pose.z), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReset = {
                        getActiveEngine().apply {
                            reset()
                            clearSurfacePlane()
                        }
                        glRenderer.breadcrumbRenderer.clear()
                        glRenderer.updateVoxelCloud(emptyList())
                        observationCount.longValue = 0L
                        searchState.value = AppSearchState.COARSE_SEARCH
                        guidanceHint.value = AppGuidanceHint.CONTINUE_SWEEP
                        confidenceRadius.value = null
                        targetBearing.value = null
                    }
                )
            }
        }
    }

    private fun getActiveEngine(): SnoopArNativeEngine {
        val addr = selectedAddress.value ?: "default"
        return synchronized(engineMap) {
            engineMap.getOrPut(addr) { SnoopArNativeEngine(0) }
        }
    }

    private fun getOrCreateEngine(address: String, trackerType: Int): SnoopArNativeEngine {
        return synchronized(engineMap) {
            engineMap.getOrPut(address) {
                if (engineMap.size >= 6) {
                    val oldestKey = engineMap.keys.firstOrNull { it != selectedAddress.value }
                    if (oldestKey != null) {
                        engineMap.remove(oldestKey)?.close()
                    }
                }
                SnoopArNativeEngine(trackerType)
            }
        }
    }

    private fun onBlePacketReceived(address: String, rssi: Float, timestampNs: Long) {
        val pose = arCoreController.interpolatePoseAt(timestampNs) ?: return

        // Auto-select first discovered tracker if none is active yet
        if (selectedAddress.value == null) {
            val tracker = trackerScanner?.discoveredTrackers?.value?.find { it.deviceAddress == address }
            if (tracker != null) {
                selectedAddress.value = tracker.deviceAddress
                targetName.value = "${tracker.family.displayName} (${tracker.deviceAddress.takeLast(5)})"
                trackerScanner?.setTargetDevice(tracker.deviceAddress)
            }
        }

        val trackerType = trackerScanner?.discoveredTrackers?.value?.find { it.deviceAddress == address }?.family?.trackerTypeId ?: 0

        // Ingest into target's dedicated engine in parallel
        val targetEngine = getOrCreateEngine(address, trackerType)

        lifecycleScope.launch(Dispatchers.Default) {
            targetEngine.addObservation(
                pose.x, pose.y, pose.z,
                pose.gazeX, pose.gazeY, pose.gazeZ,
                rssi, timestampNs
            )

            // Record in empirical capture trace if recording active
            if (isRecording.value) {
                sessionRecorder.recordObservation(
                    timestampNs, pose.x, pose.y, pose.z,
                    pose.gazeX, pose.gazeY, pose.gazeZ,
                    rssi, address
                )
                recordedSampleCount.intValue = sessionRecorder.getObservationCount()
            }

            // Only update live AR visualizations and feedback if packet is from currently selected tracker
            if (address == selectedAddress.value) {
                // Add 3D AR Breadcrumb
                glRenderer.breadcrumbRenderer.addBreadcrumb(pose.x, pose.y, pose.z, rssi)

                // If close sweep is active, periodically refresh nearest surface plane
                if (searchState.value == AppSearchState.CLOSE_SWEEP_ACTIVE) {
                    arCoreController.getNearestPlane()?.let { plane ->
                        targetEngine.setSurfacePlane(
                            plane.centerX, plane.centerY, plane.centerZ,
                            plane.normalX, plane.normalY, plane.normalZ,
                            sigmaMeters = 0.08f
                        )
                    }
                }

                // Query updated 3D Volumetric Probability Cloud
                val voxels = targetEngine.getVoxelCloud(maxCount = 80)
                glRenderer.updateVoxelCloud(voxels)

                val count = targetEngine.getObservationCount()
                val state = targetEngine.getSearchState()
                val hint = targetEngine.getGuidanceHint()
                val estimate = targetEngine.getEstimate()
                val bearing = targetEngine.getBearingToTarget(
                    pose.x, pose.y, pose.z,
                    pose.gazeX, pose.gazeY, pose.gazeZ
                )

                withContext(Dispatchers.Main) {
                    observationCount.longValue = count
                    latestRssi.floatValue = rssi
                    searchState.value = state
                    guidanceHint.value = hint
                    targetBearing.value = bearing

                    if (estimate != null) {
                        confidenceRadius.value = estimate.confidenceRadius

                        // Compute Geiger alignment dot product
                        val dx = estimate.x - pose.x
                        val dy = estimate.y - pose.y
                        val dz = estimate.z - pose.z
                        val dist = sqrt(dx * dx + dy * dy + dz * dz)
                        if (dist > 1e-4f) {
                            val dot = (pose.gazeX * (dx / dist)) + (pose.gazeY * (dy / dist)) + (pose.gazeZ * (dz / dist))
                            geigerFeedback.update(dot, dist)
                        }
                    }
                }
            }
        }
    }

    private fun onSelectTargetTracker(tracker: DiscoveredTracker) {
        selectedAddress.value = tracker.deviceAddress
        targetName.value = "${tracker.family.displayName} (${tracker.deviceAddress.takeLast(5)})"
        trackerScanner?.setTargetDevice(tracker.deviceAddress)

        // Retrieve existing parallel tracking engine (maintains Bayesian grid without wiping history)
        val engine = getOrCreateEngine(tracker.deviceAddress, tracker.family.trackerTypeId)

        // Update UI & AR state from target's accumulated state
        val count = engine.getObservationCount()
        val state = engine.getSearchState()
        val hint = engine.getGuidanceHint()
        val estimate = engine.getEstimate()
        val voxels = engine.getVoxelCloud(maxCount = 80)

        observationCount.longValue = count
        searchState.value = state
        guidanceHint.value = hint
        confidenceRadius.value = estimate?.confidenceRadius
        glRenderer.updateVoxelCloud(voxels)
    }

    private fun hasRequiredPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return cameraGranted && (locationGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && btGranted
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
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
        if (!hasRequiredPermissions()) return
        setupArCoreAndStart()
    }

    private fun setupArCoreAndStart() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    userRequestedInstall = false
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    if (arCoreController.session == null) {
                        if (arCoreController.initialize()) {
                            isSessionConfigured = true
                        } else {
                            Toast.makeText(this, "ARCore initialization failed. Ensure Google Play Services for AR is installed", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        isSessionConfigured = true
                    }

                    if (isSessionConfigured) {
                        arCoreController.resume()
                        trackerScanner?.start()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SnoopAR", "ARCore setup exception: ${e.message}", e)
            Toast.makeText(this, "ARCore initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()

        if (hasRequiredPermissions()) {
            setupArCoreAndStart()
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
        geigerFeedback.release()
        glSurfaceView.onPause()
        arCoreController.destroy()

        synchronized(engineMap) {
            engineMap.values.forEach { it.close() }
            engineMap.clear()
        }
    }
}
