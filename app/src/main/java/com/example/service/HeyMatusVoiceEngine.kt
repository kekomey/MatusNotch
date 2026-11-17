package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.system.SystemBridges

interface HeyMatusVoiceCallbacks {
  fun onActiveAudioLevelChanged(level: Float)
  fun onActivePartialTranscript(text: String)
  fun onActiveFinalTranscript(text: String)
  fun onVoiceError(message: String)
}

class HeyMatusVoiceEngine(
  private val context: Context,
  private val callbacks: HeyMatusVoiceCallbacks,
) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var recognizer: SpeechRecognizer? = null
  private var isListeningActive: Boolean = false
  private var sessionGeneration: Long = 0L
  private var lastActivePartial: String = ""

  fun startActiveCapture(initialTranscript: String = "") {
    mainHandler.post {
      if (!SystemBridges.isRecordAudioPermissionGranted(context)) {
        callbacks.onVoiceError("Grant Microphone Access in Settings to talk to Matus")
        return@post
      }
      if (!SystemBridges.isSpeechRecognitionAvailable(context)) {
        callbacks.onVoiceError("Speech recognition service unavailable on this device")
        return@post
      }
      lastActivePartial = initialTranscript
      startListeningSession()
    }
  }

  fun stopActiveCapture() {
    mainHandler.post {
      stopRecognizerInternal()
    }
  }

  private fun startListeningSession() {
    destroyRecognizer()
    isListeningActive = true
    val gen = ++sessionGeneration

    val created =
      runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    if (created == null) {
      isListeningActive = false
      callbacks.onVoiceError("Unable to start speech recognizer")
      return
    }

    recognizer = created
    created.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
          if (gen != sessionGeneration || !isListeningActive) return
          val normalized = ((rmsdB + 2f) / 11f).coerceIn(0.06f, 1f)
          callbacks.onActiveAudioLevelChanged(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
          if (gen != sessionGeneration || !isListeningActive) return
          callbacks.onActiveAudioLevelChanged(0.08f)
        }

        override fun onError(error: Int) {
          if (gen != sessionGeneration || !isListeningActive) return
          val captured = lastActivePartial.trim()
          stopRecognizerInternal()
          if (captured.isNotEmpty()) {
            callbacks.onActiveFinalTranscript(captured)
          } else if (
            error == SpeechRecognizer.ERROR_NO_MATCH ||
              error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
          ) {
            callbacks.onActiveFinalTranscript("")
          } else {
            callbacks.onVoiceError(describeSpeechError(error))
          }
        }

        override fun onResults(results: Bundle?) {
          if (gen != sessionGeneration || !isListeningActive) return
          val text =
            results
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
          val finalResult = text.ifBlank { lastActivePartial.trim() }
          stopRecognizerInternal()
          callbacks.onActiveFinalTranscript(finalResult)
        }

        override fun onPartialResults(partialResults: Bundle?) {
          if (gen != sessionGeneration || !isListeningActive) return
          val partial =
            partialResults
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
          if (partial.isBlank()) return
          lastActivePartial = partial
          callbacks.onActivePartialTranscript(partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
      }
    )

    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
      }

    runCatching { created.startListening(intent) }.onFailure {
      stopRecognizerInternal()
      callbacks.onVoiceError("Could not listen to microphone")
    }
  }

  private fun stopRecognizerInternal() {
    sessionGeneration++
    isListeningActive = false
    destroyRecognizer()
  }

  private fun destroyRecognizer() {
    recognizer?.let { rec ->
      runCatching { rec.cancel() }
      runCatching { rec.destroy() }
    }
    recognizer = null
  }

  private fun describeSpeechError(error: Int): String =
    when (error) {
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Grant Microphone Access in Settings to talk to Matus"
      SpeechRecognizer.ERROR_AUDIO -> "Microphone is currently busy with another app"
      SpeechRecognizer.ERROR_NETWORK,
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs network or offline voice pack"
      else -> "Could not recognize voice input"
    }
}
