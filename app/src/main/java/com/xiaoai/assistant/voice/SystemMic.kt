package com.xiaoai.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SystemMic(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var looping = false
    private var once = false
    private var generation = 0
    private var errors = 0

    fun available(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (_: Exception) {
        false
    }

    fun isLooping(): Boolean = looping

    fun isBusy(): Boolean = once

    fun startLoop(onText: (String, Boolean) -> Unit) {
        onMain {
            looping = false
            destroyRecognizer()
            if (!available()) return@onMain
            looping = true
            once = false
            errors = 0
            beginLoop(onText)
        }
    }

    fun recognizeOnce(onResult: (String?) -> Unit) {
        onMain {
            looping = false
            destroyRecognizer()
            if (!available()) {
                onResult(null)
                return@onMain
            }
            val token = ++generation
            once = true
            looping = false
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onResults(results: Bundle?) {
                    if (token != generation) return
                    once = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    destroyRecognizer()
                    onResult(text)
                }

                override fun onError(error: Int) {
                    if (token != generation) return
                    once = false
                    destroyRecognizer()
                    onResult(null)
                }
            })
            try {
                speech.startListening(intent(partial = false))
            } catch (_: Exception) {
                once = false
                destroyRecognizer()
                onResult(null)
                return@onMain
            }
            main.postDelayed({
                if (token == generation && once) {
                    once = false
                    destroyRecognizer()
                    onResult(null)
                }
            }, 10000)
        }
    }

    fun stop() {
        generation += 1
        looping = false
        once = false
        if (Looper.myLooper() == Looper.getMainLooper()) destroyRecognizer() else main.post { destroyRecognizer() }
    }

    private fun beginLoop(onText: (String, Boolean) -> Unit) {
        val token = generation
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                if (token != generation || !looping) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onText(text, false)
            }

            override fun onResults(results: Bundle?) {
                if (token != generation || !looping) return
                errors = 0
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onText(text, true)
                main.postDelayed({ if (looping && token == generation) restartLoop(onText) }, 200)
            }

            override fun onError(error: Int) {
                if (token != generation || !looping) return
                errors += 1
                val delayMs = if (errors > 5) 1500L else 350L
                main.postDelayed({ if (looping && token == generation) restartLoop(onText) }, delayMs)
            }
        })
        try {
            speech.startListening(intent(partial = true))
        } catch (_: Exception) {
            main.postDelayed({ if (looping) restartLoop(onText) }, 800)
        }
    }

    private fun restartLoop(onText: (String, Boolean) -> Unit) {
        destroyRecognizer()
        if (!looping) return
        beginLoop(onText)
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun intent(partial: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partial)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
