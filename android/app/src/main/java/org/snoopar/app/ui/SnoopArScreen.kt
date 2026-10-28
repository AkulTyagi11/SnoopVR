package org.snoopar.app.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.snoopar.app.ble.DiscoveredTracker
import org.snoopar.app.engine.AppGuidanceHint
import org.snoopar.app.engine.AppSearchState
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
    trackers: List<DiscoveredTracker>,
    selectedAddress: String?,
    onSelectTracker: (DiscoveredTracker) -> Unit,
    onStartCloseSweep: () -> Unit,
    onReset: () -> Unit
) {
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
            onStartCloseSweep = onStartCloseSweep,
            onReset = onReset,
            modifier = Modifier.fillMaxSize()
        )
    }
}
