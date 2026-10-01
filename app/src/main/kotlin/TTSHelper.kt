package com.praveen.siriai

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks the assistant's replies out loud (voice output), like Google Assistant/Siri.
 * Auto-switches voice language per utterance: Telugu script uses the Telugu voice,
 * everything else uses English — so a mixed Tenglish app doesn't sound garbled. */
object TTSHelper {
    private var tts: TextToSpeech? = null
    private var teluguAvailable = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // వార్నింగ్ రాకుండా Locale.Builder() వాడాము
                val teluguLocale = Locale.Builder().setLanguage("te").setRegion("IN").build()
                val teluguResult = tts?.isLanguageAvailable(teluguLocale)
                teluguAvailable = teluguResult != null &&
                    teluguResult != TextToSpeech.LANG_MISSING_DATA &&
                    teluguResult != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.language = Locale.US
            }
        }
    }

    fun speak(context: Context, text: String) {
        if (tts == null) init(context)
        if (text.isBlank()) return

        val clean = text
            .replace(Regex("[*_`#]"), "")
            .replace(Regex("https?://\\S+"), "")
            .trim()
        if (clean.isBlank()) return

        val isTelugu = clean.any { it.code in 0x0C00..0x0C7F } // Telugu Unicode block
        
        // ఇక్కడ కూడా వార్నింగ్ రాకుండా అప్‌డేట్ చేశాము
        val teluguLocale = Locale.Builder().setLanguage("te").setRegion("IN").build()
        tts?.language = if (isTelugu && teluguAvailable) teluguLocale else Locale.US

        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "siri_ai_utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
