package org.snoopar.app.feedback

import android.content.Context
import org.snoopar.app.audio.GeigerAudioSynthesizer
import org.snoopar.app.haptics.GeigerHapticFeedback

/**
 * Unified feedback coordinator orchestrating synchronized audio Geiger clicking
 * and tactile motor impulses.
 */
class GeigerFeedbackManager(context: Context) {

    val haptics = GeigerHapticFeedback(context)
    val audio = GeigerAudioSynthesizer()

    fun update(alignmentDot: Float, distanceMeters: Float) {
        haptics.update(alignmentDot, distanceMeters)
        audio.update(alignmentDot, distanceMeters)
    }

    fun setAudioMuted(muted: Boolean) {
        audio.setMuted(muted)
    }

    fun isAudioMuted(): Boolean = audio.isMuted()

    fun setHapticsEnabled(enabled: Boolean) {
        haptics.setEnabled(enabled)
    }

    fun release() {
        audio.release()
    }
}
