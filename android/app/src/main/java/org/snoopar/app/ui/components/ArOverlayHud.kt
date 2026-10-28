package org.snoopar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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

@Composable
fun ArOverlayHud(
    searchState: AppSearchState,
    guidanceHint: AppGuidanceHint,
    observationCount: Long,
    latestRssi: Float,
    targetName: String,
    confidenceRadius: Float?,
    trackerCount: Int,
    onOpenTargetSelector: () -> Unit,
    onStartCloseSweep: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Top Card: State & Guidance
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (searchState == AppSearchState.TARGET_LOCKED) Icons.Default.Lock else Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (searchState) {
                            AppSearchState.COARSE_SEARCH -> "STAGE 1: COARSE SWEEP"
                            AppSearchState.READY_FOR_CLOSE_SWEEP -> "READY FOR CLOSE SWEEP"
                            AppSearchState.CLOSE_SWEEP_ACTIVE -> "STAGE 2: CLOSE SURFACE SWEEP"
                            AppSearchState.TARGET_LOCKED -> "TARGET LOCKED (< 25cm)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Guidance Message Banner
            Surface(
                color = Color(0xCC1E1E1E),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF64B5F6))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = guidanceHint.message,
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
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
