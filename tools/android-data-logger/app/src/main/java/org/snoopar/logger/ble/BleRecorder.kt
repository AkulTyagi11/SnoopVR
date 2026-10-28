package org.snoopar.logger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.SystemClock

data class BleScanEvent(
    val timestampNs: Long,
    val deviceId: String,
    val tagType: String,
    val rssi: Int,
    val rawHex: String
)

class BleRecorder(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onPacketReceived: (BleScanEvent) -> Unit
) {
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { parseAndEmit(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { parseAndEmit(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            // Handle scan failure
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isScanning || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // Scan promiscuously without filtering so we catch all tag protocols
        val filters = listOf<ScanFilter>()

        scanner.startScan(filters, settings, scanCallback)
        isScanning = true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isScanning || bluetoothAdapter == null) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(scanCallback)
        } catch (_: Exception) {}
        isScanning = false
    }

    private fun parseAndEmit(result: ScanResult) {
        val record = result.scanRecord ?: return
        val rawBytes = record.bytes ?: ByteArray(0)
        val rawHex = rawBytes.joinToString("") { "%02x".format(it) }
        val deviceId = result.device?.address ?: "UNKNOWN"
        val rssi = result.rssi
        val timestampNs = SystemClock.elapsedRealtimeNanos()

        val tagType = classifyTag(record)

        val event = BleScanEvent(
            timestampNs = timestampNs,
            deviceId = deviceId,
            tagType = tagType,
            rssi = rssi,
            rawHex = rawHex
        )
        onPacketReceived(event)
    }

    private fun classifyTag(record: android.bluetooth.le.ScanRecord): String {
        // 1. Apple Find My (Company ID 0x004c, Type 0x12)
        val appleBytes = record.getManufacturerSpecificData(0x004c)
        if (appleBytes != null && appleBytes.isNotEmpty()) {
            if (appleBytes[0] == 0x12.toByte()) {
                return "Apple_FindMy_AirTag"
            }
            return "Apple_Other_004c"
        }

        // 2. Samsung SmartThings Find (Service 0xFD5A or Company 0x0075)
        val serviceUuids = record.serviceUuids?.map { it.uuid.toString().uppercase() } ?: emptyList()
        if (serviceUuids.any { it.contains("FD5A") } || record.getManufacturerSpecificData(0x0075) != null) {
            return "Samsung_SmartTag"
        }

        // 3. Tile (Service 0xFEED or Company 0x01D3)
        if (serviceUuids.any { it.contains("FEED") } || record.getManufacturerSpecificData(0x01D3) != null) {
            return "Tile_Tracker"
        }

        // 4. Google Find My Device (Service 0xFE2C / 0xFEAA)
        if (serviceUuids.any { it.contains("FE2C") || it.contains("FEAA") }) {
            return "Google_FindMyDevice"
        }

        return "Generic_BLE"
    }
}
