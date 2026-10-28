package org.snoopar.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Low-latency procedural PCM audio synthesizer simulating an authentic Geiger counter
 * click cadence for silent tracking tag localization.
 */
class GeigerAudioSynthesizer {

    private val sampleRate = 44100
    private val clickSampleCount = 120 // ~2.7ms pulse length
    private val clickBuffer = ShortArray(clickSampleCount)
    private var audioTrack: AudioTrack? = null
    private var isMuted = false
    private var lastClickTimeMs = 0L

    init {
        // Synthesize an authentic sharp Geiger-Mueller discharge click
        // Damped 1.5 kHz impulse pulse with exponential decay
        for (i in 0 until clickSampleCount) {
            val t = i.toFloat()
            val decay = exp(-t / 22.0f)
            val wave = sin(2.0 * Math.PI * 1500.0 * (i.toDouble() / sampleRate)).toFloat()
            clickBuffer[i] = (decay * wave * 28000.0f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = max(minBuf, clickSampleCount * 4)

            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack?.play()
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    /**
     * Updates Geiger clicking cadence based on camera alignment and Euclidean proximity.
     */
    fun update(alignmentDot: Float, distanceMeters: Float) {
        if (isMuted || audioTrack == null) return

        val nowMs = SystemClock.uptimeMillis()

        val alignmentFactor = max(0.0f, alignmentDot)
        val distanceFactor = (1.0f - (distanceMeters / 3.0f)).coerceIn(0.0f, 1.0f)
        val urgency = alignmentFactor * 0.7f + distanceFactor * 0.3f

        // Interval from 1400ms (distant/misaligned) down to 60ms (close/direct alignment)
        val intervalMs = (1400.0f - urgency * 1340.0f).toLong().coerceIn(60L, 1400L)

        if (nowMs - lastClickTimeMs >= intervalMs) {
            lastClickTimeMs = nowMs
            triggerClick()
        }
    }

    private fun triggerClick() {
        try {
            audioTrack?.write(clickBuffer, 0, clickSampleCount)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}
    }
}
