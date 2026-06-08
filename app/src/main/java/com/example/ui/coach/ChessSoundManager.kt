package com.example.ui.coach

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.sin

object ChessSoundManager {
    private const val SAMPLE_RATE = 22050

    fun playMove() {
        GlobalScope.launch(Dispatchers.Default) {
            val duration = 0.06f // 60ms
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = ShortArray(numSamples)
            val freq = 350f
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                // Simple sine wave decaying exponentially
                val envelope = (1f - (i.toFloat() / numSamples)) * 0.8f
                val value = sin(2.0 * Math.PI * freq * t) * envelope
                sample[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(sample)
        }
    }

    fun playCapture() {
        GlobalScope.launch(Dispatchers.Default) {
            val duration = 0.08f // 80ms
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = ShortArray(numSamples)
            val freq = 220f
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val envelope = (1f - (i.toFloat() / numSamples)) * 0.9f
                // Add some noise/secondary wave for capture impact
                val value = (sin(2.0 * Math.PI * freq * t) + 0.3 * sin(2.0 * Math.PI * (freq * 1.5) * t)) * envelope * 0.6
                sample[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(sample)
        }
    }

    fun playCheck() {
        GlobalScope.launch(Dispatchers.Default) {
            val duration = 0.16f // 160ms
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val envelope = (1f - (i.toFloat() / numSamples)) * 0.8f
                // A higher-alert check chime (two overlay frequencies)
                val value = (sin(2.0 * Math.PI * 880.0 * t) + 0.5 * sin(2.0 * Math.PI * 1100.0 * t)) * envelope * 0.5
                sample[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(sample)
        }
    }

    fun playGameEnd() {
        GlobalScope.launch(Dispatchers.Default) {
            // A beautiful 4-note ascending completed/solved arpeggio
            val notes = listOf(523.25f, 659.25f, 783.99f, 1046.50f) // C5, E5, G5, C6
            val noteDuration = 0.15f
            val samplesPerNote = (noteDuration * SAMPLE_RATE).toInt()
            val totalSamples = samplesPerNote * notes.size
            val sample = ShortArray(totalSamples)
            
            for (n in notes.indices) {
                val freq = notes[n]
                val offset = n * samplesPerNote
                for (i in 0 until samplesPerNote) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = (1f - (i.toFloat() / samplesPerNote)) * 0.7f
                    val value = sin(2.0 * Math.PI * freq * t) * envelope
                    sample[offset + i] = (value * Short.MAX_VALUE).toInt().toShort()
                }
            }
            playPcm(sample)
        }
    }

    private fun playPcm(pcmData: ShortArray) {
        try {
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                pcmData.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(pcmData, 0, pcmData.size)
            audioTrack.play()
            
            // Release after playing is done
            GlobalScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(1500)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    // Ignore already stopped/released
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
