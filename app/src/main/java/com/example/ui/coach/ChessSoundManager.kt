package com.example.ui.coach

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.sin

object ChessSoundManager {
    private const val SAMPLE_RATE = 22050
    private const val TAG = "ChessSoundManager"

    private const val URL_MOVE = "https://www.chess.com/chess-themes/pieces/neo/sounds/move-self.mp3"
    private const val URL_CAPTURE = "https://www.chess.com/chess-themes/pieces/neo/sounds/capture.mp3"
    private const val URL_CHECK = "https://www.chess.com/chess-themes/pieces/neo/sounds/move-check.mp3"
    private const val URL_GAME_END = "https://www.chess.com/chess-themes/pieces/neo/sounds/game-end.mp3"

    private var soundPool: SoundPool? = null
    private var soundIdMove = -1
    private var soundIdCapture = -1
    private var soundIdCheck = -1
    private var soundIdGameEnd = -1

    fun initialize(context: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build()

        // Asynchronous non-blocking pre-fetch and sound setup
        GlobalScope.launch(Dispatchers.IO) {
            loadAllSounds(context)
        }
    }

    private suspend fun loadAllSounds(context: Context) {
        try {
            val cacheDir = context.cacheDir
            
            val moveFile = downloadIfMissing(URL_MOVE, File(cacheDir, "chess_move.mp3"))
            val captureFile = downloadIfMissing(URL_CAPTURE, File(cacheDir, "chess_capture.mp3"))
            val checkFile = downloadIfMissing(URL_CHECK, File(cacheDir, "chess_check.mp3"))
            val gameEndFile = downloadIfMissing(URL_GAME_END, File(cacheDir, "chess_game_end.mp3"))

            soundPool?.let { pool ->
                if (moveFile != null && moveFile.exists()) {
                    soundIdMove = pool.load(moveFile.absolutePath, 1)
                }
                if (captureFile != null && captureFile.exists()) {
                    soundIdCapture = pool.load(captureFile.absolutePath, 1)
                }
                if (checkFile != null && checkFile.exists()) {
                    soundIdCheck = pool.load(checkFile.absolutePath, 1)
                }
                if (gameEndFile != null && gameEndFile.exists()) {
                    soundIdGameEnd = pool.load(gameEndFile.absolutePath, 1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading or Loading sound files into SoundPool", e)
        }
    }

    private fun downloadIfMissing(urlString: String, destination: File): File? {
        if (destination.exists() && destination.length() > 0) {
            return destination
        }
        try {
            val url = URL(urlString)
            url.openStream().use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            return destination
        } catch (e: Exception) {
            Log.w(TAG, "Could not download $urlString, using high-quality synthetic fallback as safe secondary option.", e)
            return null
        }
    }

    fun playMove() {
        val pool = soundPool
        val id = soundIdMove
        if (pool != null && id != -1) {
            pool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            playSyntheticMove()
        }
    }

    fun playCapture() {
        val pool = soundPool
        val id = soundIdCapture
        if (pool != null && id != -1) {
            pool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            playSyntheticCapture()
        }
    }

    fun playCheck() {
        val pool = soundPool
        val id = soundIdCheck
        if (pool != null && id != -1) {
            pool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            playSyntheticCheck()
        }
    }

    fun playGameEnd() {
        val pool = soundPool
        val id = soundIdGameEnd
        if (pool != null && id != -1) {
            pool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            playSyntheticGameEnd()
        }
    }

    // --- HIGH QUALITY PCM SYNTHETIC FALLBACKS (Ensure absolute zero latency on error) ---
    private fun playSyntheticMove() {
        GlobalScope.launch(Dispatchers.Default) {
            val duration = 0.06f
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = ShortArray(numSamples)
            val freq = 350f
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val envelope = (1f - (i.toFloat() / numSamples)) * 0.8f
                val value = sin(2.0 * Math.PI * freq * t) * envelope
                sample[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(sample)
        }
    }

    private fun playSyntheticCapture() {
        GlobalScope.launch(Dispatchers.Default) {
            val duration = 0.08f
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = ShortArray(numSamples)
            val freq = 220f
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val envelope = (1f - (i.toFloat() / numSamples)) * 0.9f
                val value = (sin(2.0 * Math.PI * freq * t) + 0.3 * sin(2.0 * Math.PI * (freq * 1.5) * t)) * envelope * 0.6
                sample[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(sample)
        }
    }

    private fun playSyntheticCheck() {
        GlobalScope.launch(Dispatchers.Default) {
            val duration = 0.16f
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val envelope = (1f - (i.toFloat() / numSamples)) * 0.8f
                val value = (sin(2.0 * Math.PI * 880.0 * t) + 0.5 * sin(2.0 * Math.PI * 1100.0 * t)) * envelope * 0.5
                sample[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            playPcm(sample)
        }
    }

    private fun playSyntheticGameEnd() {
        GlobalScope.launch(Dispatchers.Default) {
            val notes = listOf(523.25f, 659.25f, 783.99f, 1046.50f)
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
            
            GlobalScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(1500)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
