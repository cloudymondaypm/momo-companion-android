package com.xiaozhi.simple.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** Uses the guaranteed on-device API, never a network-backed recognizer. Main thread only. */
class DeviceSpeechService(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognitionGeneration = 0
    private var timeout: Runnable? = null
    private var failedLanguage: String? = null
    private var engineReady = false
    private var tts: TextToSpeech? = null
    private val utterances = mutableMapOf<String, Pair<() -> Unit, () -> Unit>>()

    init {
        tts = TextToSpeech(context) { status -> engineReady = status == TextToSpeech.SUCCESS }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { main.post { utterances.remove(id)?.first?.invoke() } }
            @Deprecated("Android callback")
            override fun onError(id: String?) { main.post { utterances.remove(id)?.second?.invoke() } }
        })
    }

    fun canRecognize(language: String): Boolean = Build.VERSION.SDK_INT >= 31 &&
        failedLanguage != language && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    fun recognize(language: String, result: (String) -> Unit, failure: () -> Unit): Boolean {
        cancelRecognition()
        if (Build.VERSION.SDK_INT < 31 || !canRecognize(language)) return false
        val generation = recognitionGeneration
        var completed = false
        fun fail() {
            if (completed || generation != recognitionGeneration) return
            completed = true
            failedLanguage = language
            cancelRecognition()
            failure()
        }
        return try {
            val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) { fail() }
                override fun onResults(results: Bundle?) {
                    if (completed || generation != recognitionGeneration) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) { fail(); return }
                    completed = true
                    cancelRecognition()
                    result(text)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            current.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
            timeout = Runnable { fail() }.also { main.postDelayed(it, 30000) }
            true
        } catch (_: Exception) { cancelRecognition(); failedLanguage = language; false }
    }

    fun finishRecognition() {
        recognizer?.stopListening()
        timeout?.let { main.removeCallbacks(it); main.postDelayed(it, 5000) }
    }

    fun cancelRecognition() {
        recognitionGeneration++
        timeout?.let(main::removeCallbacks); timeout = null
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
    }

    fun resetCapabilities() { failedLanguage = null }

    private fun localVoice(language: String, name: String) = if (!engineReady) null else
        runCatching { tts?.voices?.filter { !it.isNetworkConnectionRequired &&
            (it.locale.language == Locale.forLanguageTag(language).language ||
                (language == "fil-PH" && it.locale.language in listOf("fil", "tl"))) }?.let { voices ->
            if (name.isBlank()) voices.sortedBy { it.name }.firstOrNull()
            else voices.firstOrNull { it.name == name }
        } }.getOrNull()

    fun canSpeak(language: String, voice: String): Boolean = localVoice(language, voice) != null

    fun speak(text: String, language: String, voice: String, rate: Float, pitch: Float,
              done: () -> Unit, failed: () -> Unit): Boolean {
        val selected = localVoice(language, voice) ?: return false
        val engine = tts ?: return false
        if (engine.setVoice(selected) != TextToSpeech.SUCCESS) return false
        engine.setSpeechRate(rate.coerceIn(0.5f, 2f)); engine.setPitch(pitch.coerceIn(0.5f, 2f))
        // Split at Android's documented maximum; only the last chunk completes the reply.
        val chunks = text.chunked(TextToSpeech.getMaxSpeechInputLength())
        if (chunks.isEmpty()) return false
        chunks.forEachIndexed { index, chunk ->
            val id = UUID.randomUUID().toString()
            utterances[id] = Pair(if (index == chunks.lastIndex) done else ({}), {
                stopSpeaking(); failed()
            })
            if (engine.speak(chunk, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) {
                stopSpeaking(); return false
            }
        }
        return true
    }

    fun stopSpeaking() { utterances.clear(); tts?.stop() }
    fun release() { cancelRecognition(); stopSpeaking(); tts?.shutdown(); tts = null }
}
