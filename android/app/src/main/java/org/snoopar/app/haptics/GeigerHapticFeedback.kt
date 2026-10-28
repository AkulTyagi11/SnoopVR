package org.snoopar.app.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.max

class GeigerHapticFeedback(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastTickTimeMs = 0L
    private var isEnabled = true

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Updates geiger click cadence based on alignment with target.
     * @param alignmentDot Dot product between camera forward gaze and vector to target [-1.0, 1.0]
     * @param distanceMeters Distance to target in meters
     */
    fun update(alignmentDot: Float, distanceMeters: Float) {
        if (!isEnabled || vibrator == null || !vibrator.hasVibrator()) return

        val nowMs = SystemClock.uptimeMillis()

        // Cadence interval:
        // When pointing away or far: 1500ms
        // When pointing directly at target and < 0.5m away: 70ms
        val alignmentFactor = max(0.0f, alignmentDot) // [0.0, 1.0]
        val distanceFactor = (1.0f - (distanceMeters / 3.0f)).coerceIn(0.0f, 1.0f)
        val urgency = alignmentFactor * 0.7f + distanceFactor * 0.3f // [0.0, 1.0]

        val intervalMs = (1500.0f - urgency * 1420.0f).toLong().coerceIn(70L, 1500L)

        if (nowMs - lastTickTimeMs >= intervalMs) {
            lastTickTimeMs = nowMs
            triggerTick(urgency)
        }
    }

    private fun triggerTick(urgency: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (60 + (urgency * 195)).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(12, amplitude)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(12)
        }
    }
}
