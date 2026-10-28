package org.snoopar.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrackerScanner(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onTargetRssiReceived: (address: String, rssi: Float, timestampNs: Long) -> Unit
) {
    companion object {
        val UUID_SAMSUNG_SMARTTAG: ParcelUuid = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
        val UUID_GOOGLE_FMDN: ParcelUuid = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
        val UUID_TILE_FEED: ParcelUuid = ParcelUuid.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
        val UUID_TILE_FEEC: ParcelUuid = ParcelUuid.fromString("0000FEEC-0000-1000-8000-00805F9B34FB")
    }

    private var isScanning = false
    private var targetedAddress: String? = null
    private var lastScanStartTimeMs = 0L
    private val minScanIntervalMs = 1500L

    private val _discoveredTrackers = MutableStateFlow<List<DiscoveredTracker>>(emptyList())
    val discoveredTrackers: StateFlow<List<DiscoveredTracker>> = _discoveredTrackers.asStateFlow()

    private val trackersMap = mutableMapOf<String, DiscoveredTracker>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
        }
    }

    fun setTargetDevice(address: String?) {
        targetedAddress = address
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val now = SystemClock.uptimeMillis()
        if (isScanning || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        if (now - lastScanStartTimeMs < minScanIntervalMs) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            lastScanStartTimeMs = now
        } catch (_: Exception) {
            isScanning = false
        }
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

    private fun handleResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val address = result.device?.address ?: return
        val rssi = result.rssi
        val nowNs = if (result.timestampNanos > 0) result.timestampNanos else SystemClock.elapsedRealtimeNanos()

        val family = classify(record) ?: return

        synchronized(trackersMap) {
            val existing = trackersMap[address]
            val updated = existing?.copy(
                lastRssi = rssi,
                packetCount = existing.packetCount + 1,
                lastSeenTimestampNs = nowNs
            ) ?: DiscoveredTracker(
                deviceAddress = address,
                family = family,
                lastRssi = rssi,
                packetCount = 1,
                lastSeenTimestampNs = nowNs
            )
            trackersMap[address] = updated
            _discoveredTrackers.value = trackersMap.values.toList()
        }

        // If this is the currently locked target (or only suspect tag), emit observation
        if (targetedAddress == null || targetedAddress == address) {
            onTargetRssiReceived(address, rssi.toFloat(), nowNs)
        }
    }

    fun classify(record: ScanRecord): TrackerFamily? {
        // 1. Apple Find My (Company ID 0x004c, Type 0x12, payload length >= 25 bytes)
        val appleBytes = record.getManufacturerSpecificData(0x004c)
        if (appleBytes != null && appleBytes.size >= 25 && appleBytes[0] == 0x12.toByte()) {
            return TrackerFamily.APPLE_AIRTAG
        }

        // 2. Samsung SmartThings Find (Service data on 0xFD5A)
        val samsungServiceData = record.getServiceData(UUID_SAMSUNG_SMARTTAG)
        if (samsungServiceData != null || record.serviceUuids?.contains(UUID_SAMSUNG_SMARTTAG) == true) {
            return TrackerFamily.SAMSUNG_SMARTTAG
        }

        // 3. Tile Tracker (Exact 16-bit match on 0xFEED or 0xFEEC, or Tile company ID 0x01D3)
        if (record.serviceUuids?.contains(UUID_TILE_FEED) == true ||
            record.serviceUuids?.contains(UUID_TILE_FEEC) == true ||
            record.getManufacturerSpecificData(0x01D3) != null) {
            return TrackerFamily.TILE
        }

        // 4. Google Find My Device (Eddystone 0xFEAA with frame type 0x40 or 0x41)
        val feaaData = record.getServiceData(UUID_GOOGLE_FMDN)
        if (feaaData != null && feaaData.isNotEmpty()) {
            val frameType = feaaData[0].toInt() and 0xFF
            if (frameType == 0x40 || frameType == 0x41) {
                return TrackerFamily.GOOGLE_FMD
            }
        }

        return null
    }
}
