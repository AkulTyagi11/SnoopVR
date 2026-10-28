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
    val lastRssi: Int,
    val packetCount: Int = 1,
    val lastSeenTimestampNs: Long = 0L
)
