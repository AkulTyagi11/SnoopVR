package org.snoopar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snoopar.app.engine.AppGuidanceHint
import org.snoopar.app.engine.AppSearchState
import org.snoopar.app.engine.TargetBearing
import kotlin.math.abs

@Composable
fun ArOverlayHud(
    searchState: AppSearchState,
    guidanceHint: AppGuidanceHint,
    observationCount: Long,
    latestRssi: Float,
    targetName: String,
    confidenceRadius: Float?,
    trackerCount: Int,
    targetBearing: TargetBearing? = null,
    isAudioMuted: Boolean = false,
    isRecording: Boolean = false,
    recordedCount: Int = 0,
    hasGroundTruth: Boolean = false,
    onOpenTargetSelector: () -> Unit,
    onStartCloseSweep: () -> Unit,
    onToggleAudioMute: () -> Unit = {},
    onToggleRecording: () -> Unit = {},
    onMarkGroundTruth: () -> Unit = {},
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Top Bar: State, Guidance & Recording Controls
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stage Indicator Pill
                Surface(
                    color = when (searchState) {
                        AppSearchState.COARSE_SEARCH -> Color(0xCC1565C0)
                        AppSearchState.READY_FOR_CLOSE_SWEEP -> Color(0xCCF57C00)
                        AppSearchState.CLOSE_SWEEP_ACTIVE -> Color(0xCCE65100)
                        AppSearchState.TARGET_LOCKED -> Color(0xCC2E7D32)
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (searchState == AppSearchState.TARGET_LOCKED) Icons.Default.Lock else Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (searchState) {
                                AppSearchState.COARSE_SEARCH -> "STAGE 1: COARSE"
                                AppSearchState.READY_FOR_CLOSE_SWEEP -> "CLOSE SWEEP READY"
                                AppSearchState.CLOSE_SWEEP_ACTIVE -> "STAGE 2: CLOSE SWEEP"
                                AppSearchState.TARGET_LOCKED -> "TARGET LOCKED (<25cm)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // In-App Session Recording Toggle (Empirical Capture)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isRecording) {
                        Surface(
                            color = if (hasGroundTruth) Color(0xDD2E7D32) else Color(0xDD00838F),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { onMarkGroundTruth() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = "Pin Tag", tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (hasGroundTruth) "Pinned" else "Pin Tag", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Surface(
                        color = if (isRecording) Color(0xDDCC0000) else Color(0x88263238),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onToggleRecording() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = "Toggle Recording",
                                tint = if (isRecording) Color.White else Color(0xFFFF5252),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRecording) "REC ($recordedCount)" else "REC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Guidance Message Banner
            Surface(
                color = Color(0xCC1E1E1E),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.96f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF64B5F6))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = guidanceHint.message,
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Directional Guidance Indicator (Off-Screen & Range)
            if (targetBearing != null && targetBearing.distanceM > 0.05f) {
                val isLeft = targetBearing.azimuthDeg < -25f
                val isRight = targetBearing.azimuthDeg > 25f
                val absAzimuth = abs(targetBearing.azimuthDeg).toInt()
                val distStr = "%.1fm".format(targetBearing.distanceM)

                val navText = when {
                    isLeft -> "← Turn Left ${absAzimuth}° ($distStr)"
                    isRight -> "Turn Right ${absAzimuth}° → ($distStr)"
                    else -> "Target Ahead ($distStr)"
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = if (isLeft || isRight) Color(0xDD00838F) else Color(0xDD2E7D32),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = navText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Bar: Telemetry & Actions
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xCC121212),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTargetSelector() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(targetName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0x3364B5F6),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "$trackerCount found",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64B5F6),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "Samples: $observationCount | Rad: ${confidenceRadius?.let { "%.1f cm".format(it * 100) } ?: "--"}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.LightGray
                            )
                        }

                        Text(
                            text = if (latestRssi > -99f) "%.0f dBm".format(latestRssi) else "--",
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                latestRssi >= -65f -> Color(0xFF4CAF50)
                                latestRssi >= -75f -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenTargetSelector,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238))
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Trackers", fontSize = 13.sp)
                        }

                        if (searchState == AppSearchState.READY_FOR_CLOSE_SWEEP || searchState == AppSearchState.COARSE_SEARCH) {
                            Button(
                                onClick = onStartCloseSweep,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                            ) {
                                Text("Begin Close Sweep", fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = onToggleAudioMute,
                            modifier = Modifier.background(
                                if (isAudioMuted) Color(0x33F44336) else Color(0x334CAF50),
                                RoundedCornerShape(8.dp)
                            )
                        ) {
                            Icon(
                                imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isAudioMuted) "Unmute Geiger Audio" else "Mute Geiger Audio",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onReset,
                            modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
