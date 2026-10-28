package org.snoopar.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrackerScanner(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onTargetRssiReceived: (address: String, rssi: Float, timestampNs: Long) -> Unit
) {
    private var isScanning = false
    private var targetedAddress: String? = null

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

        override fun onScanFailed(errorCode: Int) {}
    }

    fun setTargetDevice(address: String?) {
        targetedAddress = address
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
        if (!isScanning || bluetoothAdapter == null) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(scanCallback)
        } catch (_: Exception) {}
        isScanning = false
    }

    private fun handleResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val address = result.device?.address ?: return
        val rssi = result.rssi
        val nowNs = SystemClock.elapsedRealtimeNanos()

        val family = classify(record) ?: return

        synchronized(trackersMap) {
            val existing = trackersMap[address]
            if (existing != null) {
                existing.lastRssi = rssi
                existing.packetCount += 1
                existing.lastSeenTimestampNs = nowNs
            } else {
                trackersMap[address] = DiscoveredTracker(
                    deviceAddress = address,
                    family = family,
                    lastRssi = rssi,
                    packetCount = 1,
                    lastSeenTimestampNs = nowNs
                )
            }
            _discoveredTrackers.value = trackersMap.values.toList()
        }

        // If this is the currently locked target (or only suspect tag), emit observation
        if (targetedAddress == null || targetedAddress == address) {
            onTargetRssiReceived(address, rssi.toFloat(), nowNs)
        }
    }

    private fun classify(record: android.bluetooth.le.ScanRecord): TrackerFamily? {
        val appleBytes = record.getManufacturerSpecificData(0x004c)
        if (appleBytes != null && appleBytes.isNotEmpty() && appleBytes[0] == 0x12.toByte()) {
            return TrackerFamily.APPLE_AIRTAG
        }

        val uuids = record.serviceUuids?.map { it.uuid.toString().uppercase() } ?: emptyList()
        if (uuids.any { it.contains("FD5A") } || record.getManufacturerSpecificData(0x0075) != null) {
            return TrackerFamily.SAMSUNG_SMARTTAG
        }

        if (uuids.any { it.contains("FEED") } || record.getManufacturerSpecificData(0x01D3) != null) {
            return TrackerFamily.TILE
        }

        if (uuids.any { it.contains("FE2C") || it.contains("FEAA") }) {
            return TrackerFamily.GOOGLE_FMD
        }

        return null
    }
}
