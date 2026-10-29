package org.snoopar.app.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedObservation(
    val timestampNs: Long,
    val x: Float, val y: Float, val z: Float,
    val gazeX: Float, val gazeY: Float, val gazeZ: Float,
    val rssi: Float,
    val deviceAddress: String
)

class SessionRecorder {

    private var isRecording = false
    private var recordingStartTimeMs = 0L
    private var targetedAddress: String = ""
    private var trackerTypeId: Int = 0
    private var groundTruth: FloatArray? = null

    private val observations = mutableListOf<RecordedObservation>()

    fun isRecording(): Boolean = isRecording

    fun getObservationCount(): Int = synchronized(observations) { observations.size }

    fun startRecording(targetAddress: String, trackerType: Int) {
        synchronized(observations) {
            observations.clear()
            isRecording = true
            recordingStartTimeMs = System.currentTimeMillis()
            targetedAddress = targetAddress
            trackerTypeId = trackerType
            groundTruth = null
        }
    }

    fun recordObservation(
        timestampNs: Long,
        x: Float, y: Float, z: Float,
        gazeX: Float, gazeY: Float, gazeZ: Float,
        rssi: Float,
        deviceAddress: String
    ) {
        if (!isRecording) return

        synchronized(observations) {
            observations.add(
                RecordedObservation(
                    timestampNs = timestampNs,
                    x = x, y = y, z = z,
                    gazeX = gazeX, gazeY = gazeY, gazeZ = gazeZ,
                    rssi = rssi,
                    deviceAddress = deviceAddress
                )
            )
        }
    }

    fun setGroundTruth(x: Float, y: Float, z: Float) {
        groundTruth = floatArrayOf(x, y, z)
    }

    fun hasGroundTruth(): Boolean = groundTruth != null

    fun stopRecording(context: Context): String? {
        val records: List<RecordedObservation>
        val gt: FloatArray?
        val address: String
        val trackerType: Int

        synchronized(observations) {
            if (!isRecording) return null
            isRecording = false
            records = observations.toList()
            gt = groundTruth
            address = targetedAddress
            trackerType = trackerTypeId
            observations.clear()
        }

        if (records.isEmpty()) return null

        return try {
            val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "snoopar_session_$timeStampStr.json"

            // Target public Downloads directory if accessible, otherwise app external documents directory
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val snoopArDir = File(baseDir, "SnoopAR")
            if (!snoopArDir.exists()) {
                snoopArDir.mkdirs()
            }

            val targetFile = if (snoopArDir.canWrite()) {
                File(snoopArDir, fileName)
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            }

            val rootJson = JSONObject().apply {
                put("session_id", "snoopar_$timeStampStr")
                put("created_at_epoch_ms", recordingStartTimeMs)
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("os_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                put("target_device_address", address)
                put("tracker_type_id", trackerType)
                put("observation_count", records.size)

                if (gt != null && gt.size >= 3) {
                    val gtArr = JSONArray().apply {
                        put(gt[0].toDouble())
                        put(gt[1].toDouble())
                        put(gt[2].toDouble())
                    }
                    put("ground_truth_position", gtArr)
                }

                val obsArr = JSONArray()
                for (obs in records) {
                    val obsObj = JSONObject().apply {
                        put("t_ns", obs.timestampNs)
                        put("x", obs.x.toDouble())
                        put("y", obs.y.toDouble())
                        put("z", obs.z.toDouble())
                        put("gx", obs.gazeX.toDouble())
                        put("gy", obs.gazeY.toDouble())
                        put("gz", obs.gazeZ.toDouble())
                        put("rssi", obs.rssi.toDouble())
                        put("addr", obs.deviceAddress)
                    }
                    obsArr.put(obsObj)
                }
                put("observations", obsArr)
            }

            FileWriter(targetFile).use { writer ->
                writer.write(rootJson.toString(2))
            }

            Log.i("SnoopAR", "Saved session trace to ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e("SnoopAR", "Failed to save session trace: ${e.message}", e)
            null
        }
    }
}
