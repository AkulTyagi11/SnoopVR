package org.snoopar.logger.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvSessionWriter(private val context: Context) {

    private var sessionDir: File? = null
    private var posesWriter: BufferedWriter? = null
    private var bleWriter: BufferedWriter? = null
    private var poseCounter = 0
    private var bleCounter = 0

    @Volatile
    var isRecording = false
        private set

    var currentSessionPath: String = ""
        private set

    private var groundTruthPos: FloatArray? = null

    @Synchronized
    fun startSession(notes: String = "", groundTruth: FloatArray? = null): String {
        groundTruthPos = groundTruth
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
            ?: context.filesDir
        val dir = File(baseDir, "SnoopAR_Sessions/session_$timestamp")
        dir.mkdirs()
        sessionDir = dir
        currentSessionPath = dir.absolutePath
        poseCounter = 0
        bleCounter = 0

        // 1. Initialize poses.csv
        val posesFile = File(dir, "poses.csv")
        posesWriter = BufferedWriter(FileWriter(posesFile))
        posesWriter?.write("timestamp_ns,pos_x,pos_y,pos_z,qw,qx,qy,qz,tracking_state\n")
        posesWriter?.flush()

        // 2. Initialize ble.csv
        val bleFile = File(dir, "ble.csv")
        bleWriter = BufferedWriter(FileWriter(bleFile))
        bleWriter?.write("timestamp_ns,device_id,tag_type,rssi,raw_hex\n")
        bleWriter?.flush()

        // 3. Initialize metadata.json
        writeMetadata(notes, groundTruth)

        isRecording = true
        return currentSessionPath
    }

    @Synchronized
    fun setGroundTruth(x: Float, y: Float, z: Float) {
        groundTruthPos = floatArrayOf(x, y, z)
        writeMetadata("Updated ground truth", groundTruthPos)
    }

    private fun writeMetadata(notes: String, groundTruth: FloatArray?) {
        val dir = sessionDir ?: return
        val metaFile = File(dir, "metadata.json")
        val json = JSONObject().apply {
            put("session_dir", dir.name)
            put("start_time", Date().toString())
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android_version", Build.VERSION.RELEASE)
            put("notes", notes)
            if (groundTruth != null && groundTruth.size >= 3) {
                put("ground_truth_position", JSONArray().apply {
                    put(groundTruth[0].toDouble())
                    put(groundTruth[1].toDouble())
                    put(groundTruth[2].toDouble())
                })
            }
        }
        metaFile.writeText(json.toString(2))
    }

    @Synchronized
    fun logPose(
        timestampNs: Long,
        x: Float, y: Float, z: Float,
        qw: Float, qx: Float, qy: Float, qz: Float,
        trackingState: String
    ) {
        if (!isRecording) return
        posesWriter?.apply {
            write("$timestampNs,$x,$y,$z,$qw,$qx,$qy,$qz,$trackingState\n")
            poseCounter++
            if (poseCounter % 30 == 0) {
                flush() // Flush every ~1 second
            }
        }
    }

    @Synchronized
    fun logBle(
        timestampNs: Long,
        deviceId: String,
        tagType: String,
        rssi: Int,
        rawHex: String
    ) {
        if (!isRecording) return
        bleWriter?.apply {
            write("$timestampNs,$deviceId,$tagType,$rssi,$rawHex\n")
            bleCounter++
            if (bleCounter % 5 == 0) {
                flush()
            }
        }
    }

    @Synchronized
    fun stopSession() {
        if (!isRecording) return
        isRecording = false
        try {
            posesWriter?.flush()
            posesWriter?.close()
        } catch (_: Exception) {}
        try {
            bleWriter?.flush()
            bleWriter?.close()
        } catch (_: Exception) {}
        posesWriter = null
        bleWriter = null
    }
}
