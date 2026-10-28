package org.snoopar.logger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.snoopar.logger.ar.ArPoseRecorder
import org.snoopar.logger.ble.BleRecorder
import org.snoopar.logger.storage.CsvSessionWriter
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var sessionWriter: CsvSessionWriter
    private var bleRecorder: BleRecorder? = null
    private var arRecorder: ArPoseRecorder? = null
    private lateinit var glSurfaceView: GLSurfaceView

    // UI State
    private val isRecordingState = mutableStateOf(false)
    private val hasPermissionsState = mutableStateOf(false)
    private val poseCountState = mutableStateOf(0)
    private val bleCountState = mutableStateOf(0)
    private val latestRssiState = mutableStateOf(-100)
    private val latestTagTypeState = mutableStateOf("None")
    private val latestPoseState = mutableStateOf("[0.0, 0.0, 0.0]")
    private val groundTruthState = mutableStateOf<String?>(null)
    private val sessionPathState = mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        hasPermissionsState.value = allGranted
        if (allGranted) {
            initArCore()
        } else {
            Toast.makeText(this, "Camera and Bluetooth permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionWriter = CsvSessionWriter(this)

        arRecorder = ArPoseRecorder(
            context = this,
            onPoseAvailable = { poseEvent ->
                if (sessionWriter.isRecording && poseEvent.trackingState == "TRACKING") {
                    sessionWriter.logPose(
                        poseEvent.clockTimestampNs,
                        poseEvent.x, poseEvent.y, poseEvent.z,
                        poseEvent.qw, poseEvent.qx, poseEvent.qy, poseEvent.qz,
                        poseEvent.trackingState
                    )
                    poseCountState.value += 1
                }
                latestPoseState.value = "[%.2f, %.2f, %.2f]".format(poseEvent.x, poseEvent.y, poseEvent.z)
            },
            onGroundTruthTapped = { x, y, z ->
                groundTruthState.value = "[%.2f, %.2f, %.2f]".format(x, y, z)
                sessionWriter.setGroundTruth(x, y, z)
                Toast.makeText(this, "Ground Truth Tag Pinned: [%.2f, %.2f, %.2f]".format(x, y, z), Toast.LENGTH_SHORT).show()
            }
        )

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(arRecorder)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, event ->
                arRecorder?.onTouch(event)
                true
            }
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        bleRecorder = BleRecorder(bluetoothAdapter) { event ->
            if (sessionWriter.isRecording) {
                sessionWriter.logBle(
                    event.timestampNs,
                    event.deviceId,
                    event.tagType,
                    event.rssi,
                    event.rawHex
                )
                bleCountState.value += 1
            }
            if (event.tagType != "Generic_BLE") {
                latestRssiState.value = event.rssi
                latestTagTypeState.value = "${event.tagType} (${event.deviceId.takeLast(5)})"
            }
        }

        checkAndRequestPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Real Camera Preview
                        AndroidView(
                            factory = { glSurfaceView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Logger Controls Overlay
                        LoggerScreen(
                            isRecording = isRecordingState.value,
                            hasPermissions = hasPermissionsState.value,
                            poseCount = poseCountState.value,
                            bleCount = bleCountState.value,
                            latestRssi = latestRssiState.value,
                            latestTagType = latestTagTypeState.value,
                            latestPose = latestPoseState.value,
                            groundTruth = groundTruthState.value,
                            sessionPath = sessionPathState.value,
                            onStartSession = { startRecording() },
                            onStopSession = { stopRecording() },
                            onShareSession = { shareSession() }
                        )
                    }
                }
            }
        }
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
            hasPermissionsState.value = false
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            hasPermissionsState.value = true
            initArCore()
        }
    }

    private fun initArCore() {
        arRecorder?.initialize()
    }

    private fun startRecording() {
        if (!hasPermissionsState.value) {
            checkAndRequestPermissions()
            return
        }
        poseCountState.value = 0
        bleCountState.value = 0
        val path = sessionWriter.startSession("Sweep session with ground truth")
        sessionPathState.value = path
        isRecordingState.value = true

        bleRecorder?.start()
    }

    private fun stopRecording() {
        if (!isRecordingState.value) return
        sessionWriter.stopSession()
        bleRecorder?.stop()
        isRecordingState.value = false
        Toast.makeText(this, "Session saved: ${sessionPathState.value}", Toast.LENGTH_LONG).show()
    }

    private fun shareSession() {
        val dir = File(sessionPathState.value)
        if (!dir.exists()) return
        val posesFile = File(dir, "poses.csv")
        if (!posesFile.exists()) return

        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.provider", posesFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Export SnoopAR CSV"))
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissionsState.value) {
            glSurfaceView.onResume()
            arRecorder?.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecordingState.value) {
            stopRecording()
        }
        arRecorder?.pause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        arRecorder?.destroy()
    }
}

@Composable
fun LoggerScreen(
    isRecording: Boolean,
    hasPermissions: Boolean,
    poseCount: Int,
    bleCount: Int,
    latestRssi: Int,
    latestTagType: String,
    latestPose: String,
    groundTruth: String?,
    sessionPath: String,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onShareSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Surface(
            color = Color(0xCC000000),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SnoopAR Data Logger", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Tap tag in camera view to pin ground truth", fontSize = 12.sp, color = Color(0xFF64B5F6))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC1E1E1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isRecording) Color.Red else Color.Gray,
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRecording) "RECORDING ACTIVE" else "IDLE / READY",
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color.Red else Color.LightGray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Poses Logged (30Hz): $poseCount", fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 13.sp)
                Text("BLE Packets Logged: $bleCount", fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 13.sp)
                Text("Camera Pose: $latestPose", fontFamily = FontFamily.Monospace, color = Color(0xFF64B5F6), fontSize = 13.sp)
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (groundTruth != null) "Ground Truth: $groundTruth" else "Tap screen on tag to set Ground Truth",
                        fontFamily = FontFamily.Monospace,
                        color = if (groundTruth != null) Color(0xFFFF9800) else Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Target RF Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC1E1E1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("SUSPECT TRACKER TELEMETRY", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Detected: $latestTagType", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (latestRssi > -99) "RSSI: $latestRssi dBm" else "RSSI: No Tag In Range",
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        latestRssi > -65 -> Color(0xFF4CAF50)
                        latestRssi > -75 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        if (!isRecording) {
            Button(
                onClick = onStartSession,
                enabled = hasPermissions,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Empirical Sweep", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStopSession,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop & Save CSVs", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (sessionPath.isNotEmpty() && !isRecording) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onShareSession,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export / Share poses.csv")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Saved in app documents",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.LightGray
            )
        }
    }
}
