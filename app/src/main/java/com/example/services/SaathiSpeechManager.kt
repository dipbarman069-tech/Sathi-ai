package com.example.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.AssistantLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages natural female Hindi/Hinglish/English TTS and push-to-talk SpeechRecognizer
 * with live RMS energy levels for the animated Voice Orb.
 */
class SaathiSpeechManager(private val context: Context) {
  private var tts: TextToSpeech? = null
  private var ttsReady = false
  private var speechRecognizer: SpeechRecognizer? = null

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _rmsLevel = MutableStateFlow(0f)
  val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

  private val _partialTranscript = MutableStateFlow("")
  val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

  private var preferredLanguage = AssistantLanguage.HINGLISH
  private var pitch = 1.12f
  private var rate = 1.0f

  init {
    initTts()
  }

  private fun initTts() {
    tts =
      TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          ttsReady = true
          applyVoiceConfig(preferredLanguage, pitch, rate)
          tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
              }

              override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
              }
            }
          )
        }
      }
  }

  fun applyVoiceConfig(language: AssistantLanguage, newPitch: Float, newRate: Float) {
    preferredLanguage = language
    pitch = newPitch
    rate = newRate
    val engine = tts ?: return
    if (!ttsReady) return

    val targetLocale =
      when (language) {
        AssistantLanguage.ENGLISH -> Locale("en", "IN")
        AssistantLanguage.HINDI,
        AssistantLanguage.HINGLISH,
        AssistantLanguage.AUTO -> Locale("hi", "IN")
      }

    engine.language = targetLocale
    engine.setPitch(newPitch)
    engine.setSpeechRate(newRate)

    // Prefer natural female Indian Hindi/English voices when installed on device
    val femaleVoice =
      engine.voices?.firstOrNull { voice ->
        val name = voice.name.lowercase()
        val langMatch =
          voice.locale.language == targetLocale.language ||
            (language == AssistantLanguage.HINGLISH && voice.locale.language in listOf("hi", "en"))
        langMatch &&
          (name.contains("hia") ||
            name.contains("hic") ||
            name.contains("ena") ||
            name.contains("female") ||
            name.contains("neural") ||
            name.contains("-f-"))
      }
    if (femaleVoice != null) {
      engine.voice = femaleVoice
    }
  }

  fun speak(text: String) {
    if (!ttsReady || text.isBlank()) return
    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saathi_${System.currentTimeMillis()}")
  }

  fun startListening(
    language: AssistantLanguage,
    preferOffline: Boolean,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    stopListening()
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      onError("On-device Speech Recognition service unavailable on this device.")
      return
    }
    _partialTranscript.value = ""
    _isListening.value = true

    speechRecognizer =
      SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
        setRecognitionListener(
          object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
              // Normalize typical SpeechRecognizer rmsdB [-2..12] into [0..1]
              _rmsLevel.value = ((rmsdB + 2f) / 14f).coerceIn(0.05f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
              _isListening.value = false
              _rmsLevel.value = 0f
            }

            override fun onError(error: Int) {
              _isListening.value = false
              _rmsLevel.value = 0f
              val msg =
                when (error) {
                  SpeechRecognizer.ERROR_NO_MATCH -> "Kuch samajh nahi aaya, dobara bolein."
                  SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz sunai nahi di."
                  SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission chahiye."
                  SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                  else -> "Voice recognition interrupted (code $error)."
                }
              onError(msg)
            }

            override fun onResults(results: Bundle?) {
              _isListening.value = false
              _rmsLevel.value = 0f
              val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
              _partialTranscript.value = ""
              if (best.isNotBlank()) onResult(best) else onError("Kuch sunai nahi diya.")
            }

            override fun onPartialResults(partialResults: Bundle?) {
              val partial =
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
              if (partial.isNotBlank()) {
                _partialTranscript.value = partial
              }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
          }
        )

        val intent =
          Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (preferOffline) {
              putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
          }
        startListening(intent)
      }
  }

  fun stopListening() {
    _isListening.value = false
    _rmsLevel.value = 0f
    try {
      speechRecognizer?.stopListening()
      speechRecognizer?.destroy()
    } catch (_: Throwable) {}
    speechRecognizer = null
  }

  fun stopAllAudio() {
    stopListening()
    _isSpeaking.value = false
    try {
      tts?.stop()
    } catch (_: Throwable) {}
  }

  fun release() {
    stopAllAudio()
    try {
      tts?.shutdown()
    } catch (_: Throwable) {}
    tts = null
  }
}
