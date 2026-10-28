package org.snoopar.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snoopar.app.ble.DiscoveredTracker

@Composable
fun TargetSelectorSheet(
    trackers: List<DiscoveredTracker>,
    selectedAddress: String?,
    onSelectTracker: (DiscoveredTracker) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = Color(0xFF64B5F6))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suspect Trackers (${trackers.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (trackers.isEmpty()) {
                Text(
                    "Scanning for nearby AirTags, SmartTags, and Tiles...",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(trackers) { tracker ->
                        val isSelected = tracker.deviceAddress == selectedAddress
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTracker(tracker) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = tracker.family.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF64B5F6) else Color.White
                                )
                                Text(
                                    text = "${tracker.deviceAddress} | ${tracker.packetCount} pkts",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${tracker.lastRssi} dBm",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        tracker.lastRssi >= -65 -> Color(0xFF4CAF50)
                                        tracker.lastRssi >= -75 -> Color(0xFFFF9800)
                                        else -> Color(0xFFF44336)
                                    }
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Divider(color = Color(0x22FFFFFF))
                    }
                }
            }
        }
    }
}
