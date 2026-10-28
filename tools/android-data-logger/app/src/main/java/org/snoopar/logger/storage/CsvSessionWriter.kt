package org.snoopar.logger.storage

import android.content.Context
import android.os.Build
import android.os.Environment
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

    var isRecording = false
        private set

    var currentSessionPath: String = ""
        private set

    fun startSession(notes: String = ""): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
            ?: context.filesDir
        val dir = File(baseDir, "SnoopAR_Sessions/session_$timestamp")
        dir.mkdirs()
        sessionDir = dir
        currentSessionPath = dir.absolutePath

        // 1. Initialize poses.csv
        val posesFile = File(dir, "poses.csv")
        posesWriter = BufferedWriter(FileWriter(posesFile))
        posesWriter?.write("timestamp_ns,pos_x,pos_y,pos_z,qw,qx,qy,qz,tracking_state\n")

        // 2. Initialize ble.csv
        val bleFile = File(dir, "ble.csv")
        bleWriter = BufferedWriter(FileWriter(bleFile))
        bleWriter?.write("timestamp_ns,device_id,tag_type,rssi,raw_hex\n")

        // 3. Initialize metadata.json
        val metaFile = File(dir, "metadata.json")
        metaFile.writeText(
            """
            {
              "session_id": "session_$timestamp",
              "start_time": "${Date()}",
              "device_model": "${Build.MANUFACTURER} ${Build.MODEL}",
              "android_version": "${Build.VERSION.RELEASE}",
              "notes": "$notes"
            }
            """.trimIndent()
        )

        isRecording = true
        return currentSessionPath
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
        }
    }

    fun stopSession() {
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
