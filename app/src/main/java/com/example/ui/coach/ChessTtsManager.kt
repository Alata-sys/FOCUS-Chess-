package com.example.ui.coach

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class ChessTtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try setting language to French since this is FOCUS+ in French!
                val result = tts?.setLanguage(Locale.FRENCH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("ChessTtsManager", "French language not supported, using default Locale")
                    tts?.setLanguage(Locale.getDefault())
                }
                isInitialized = true
            } else {
                Log.e("ChessTtsManager", "Initialization of TTS failed")
            }
        }
    }

    fun speak(text: String) {
        if (!isInitialized) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "focus_plus_tts_id")
        } catch (e: Exception) {
            Log.e("ChessTtsManager", "Exception during TTS speak", e)
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        if (isInitialized) {
            tts?.shutdown()
        }
    }
}
