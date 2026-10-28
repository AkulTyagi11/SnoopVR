package org.snoopar.logger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
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
    companion object {
        val UUID_SAMSUNG_SMARTTAG = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
        val UUID_GOOGLE_FMDN = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
        val UUID_TILE_FEED = ParcelUuid.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
        val UUID_TILE_FEEC = ParcelUuid.fromString("0000FEEC-0000-1000-8000-00805F9B34FB")
    }

    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { parseAndEmit(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { parseAndEmit(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
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

        scanner.startScan(null, settings, scanCallback)
        isScanning = true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isScanning || bluetoothAdapter == null) {
            isScanning = false
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: Exception) {}
        }
        isScanning = false
    }

    private fun parseAndEmit(result: ScanResult) {
        val record = result.scanRecord ?: return
        val rawBytes = record.bytes ?: ByteArray(0)
        val rawHex = rawBytes.joinToString("") { "%02x".format(it) }
        val deviceId = result.device?.address ?: "UNKNOWN"
        val rssi = result.rssi
        val timestampNs = if (result.timestampNanos > 0) result.timestampNanos else SystemClock.elapsedRealtimeNanos()

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

    fun classifyTag(record: android.bluetooth.le.ScanRecord): String {
        // 1. Apple Find My (Company ID 0x004c, Type 0x12, length >= 25 bytes)
        val appleBytes = record.getManufacturerSpecificData(0x004c)
        if (appleBytes != null && appleBytes.size >= 25 && appleBytes[0] == 0x12.toByte()) {
            return "Apple_FindMy_Tag"
        }

        // 2. Samsung SmartThings Find (Service data on 0xFD5A)
        val samsungServiceData = record.getServiceData(UUID_SAMSUNG_SMARTTAG)
        if (samsungServiceData != null || record.serviceUuids?.contains(UUID_SAMSUNG_SMARTTAG) == true) {
            return "Samsung_SmartTag"
        }

        // 3. Tile Tracker (Exact 16-bit match on 0xFEED or 0xFEEC)
        if (record.serviceUuids?.contains(UUID_TILE_FEED) == true ||
            record.serviceUuids?.contains(UUID_TILE_FEEC) == true ||
            record.getManufacturerSpecificData(0x01D3) != null) {
            return "Tile_Tracker"
        }

        // 4. Google Find My Device (Eddystone 0xFEAA with frame type 0x40 or 0x41)
        val feaaData = record.getServiceData(UUID_GOOGLE_FMDN)
        if (feaaData != null && feaaData.isNotEmpty()) {
            val frameType = feaaData[0].toInt() and 0xFF
            if (frameType == 0x40 || frameType == 0x41) {
                return "Google_FindMyDevice"
            }
        }

        return "Generic_BLE"
    }
}
