package org.snoopar.app.ui

import android.opengl.GLSurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.snoopar.app.ble.DiscoveredTracker
import org.snoopar.app.engine.AppGuidanceHint
import org.snoopar.app.engine.AppSearchState
import org.snoopar.app.engine.TargetBearing
import org.snoopar.app.ui.components.ArOverlayHud
import org.snoopar.app.ui.components.TargetSelectorSheet

@Composable
fun SnoopArScreen(
    glSurfaceView: GLSurfaceView,
    searchState: AppSearchState,
    guidanceHint: AppGuidanceHint,
    observationCount: Long,
    latestRssi: Float,
    targetName: String,
    confidenceRadius: Float?,
    targetBearing: TargetBearing? = null,
    isAudioMuted: Boolean = false,
    trackers: List<DiscoveredTracker>,
    selectedAddress: String?,
    onSelectTracker: (DiscoveredTracker) -> Unit,
    onStartCloseSweep: () -> Unit,
    onToggleAudioMute: () -> Unit = {},
    onReset: () -> Unit
) {
    var showTargetSelector by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // AR OpenGL Surface
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier.fillMaxSize()
        )

        // HUD Overlay
        ArOverlayHud(
            searchState = searchState,
            guidanceHint = guidanceHint,
            observationCount = observationCount,
            latestRssi = latestRssi,
            targetName = targetName,
            confidenceRadius = confidenceRadius,
            trackerCount = trackers.size,
            targetBearing = targetBearing,
            isAudioMuted = isAudioMuted,
            onOpenTargetSelector = { showTargetSelector = true },
            onStartCloseSweep = onStartCloseSweep,
            onToggleAudioMute = onToggleAudioMute,
            onReset = onReset,
            modifier = Modifier.fillMaxSize()
        )

        // Target Selector Bottom Sheet
        AnimatedVisibility(
            visible = showTargetSelector,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TargetSelectorSheet(
                trackers = trackers,
                selectedAddress = selectedAddress,
                onSelectTracker = { tracker ->
                    onSelectTracker(tracker)
                    showTargetSelector = false
                },
                onClose = { showTargetSelector = false }
            )
        }
    }
}
