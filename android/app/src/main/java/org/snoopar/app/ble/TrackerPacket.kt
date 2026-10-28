package org.snoopar.app.ble

enum class TrackerFamily(val displayName: String, val trackerTypeId: Int) {
    APPLE_AIRTAG("Apple AirTag", 0),
    SAMSUNG_SMARTTAG("Samsung SmartTag", 1),
    TILE("Tile Tracker", 2),
    GOOGLE_FMD("Google Find My", 3),
    GENERIC_BEACON("Generic BLE Beacon", 3)
}

data class DiscoveredTracker(
    val deviceAddress: String,
    val family: TrackerFamily,
    var lastRssi: Int,
    var packetCount: Int = 1,
    var lastSeenTimestampNs: Long = System.nanoTime()
)
