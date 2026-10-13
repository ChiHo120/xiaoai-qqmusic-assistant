package com.xiaoai.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.ArrayDeque
import java.util.Locale

class Speaker(
    context: Context,
    private val onTalking: (Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Item>()
    private var ready = false
    private var failed = false
    private var speaking = false
    private var talking = false
    private var session = 0
    private var current: Item? = null

    private val tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post { onEngineReady(status) }
        }
    }

    private fun onEngineReady(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            tts.setSpeechRate(1.0f)
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            pump()
        } else {
            failed = true
            drain()
        }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        val mySession = session
        main.post {
            if (mySession != session) return@post
            if (failed || text.isBlank()) {
                onDone()
                return@post
            }
            queue.add(Item(text, onDone, mySession))
            if (ready) pump()
        }
    }

    fun stop() {
        session += 1
        queue.clear()
        speaking = false
        current = null
        if (talking) {
            talking = false
            onTalking(false)
        }
        tts.stop()
    }

    fun isSpeaking(): Boolean = speaking

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun pump() {
        if (speaking) return
        val item = queue.pollFirst() ?: run {
            if (talking) {
                talking = false
                onTalking(false)
            }
            return
        }
        if (item.session != session) {
            pump()
            return
        }
        if (!talking) {
            talking = true
            onTalking(true)
        }
        speaking = true
        current = item
        val utteranceId = "u${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(item)
            override fun onError(utteranceId: String?) = finish(item)
        })
        val spoken = tts.speak(item.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        if (spoken == TextToSpeech.ERROR) {
            finish(item)
            return
        }
        val timeout = (item.text.length * 280L + 2500L).coerceIn(4000L, 20000L)
        main.postDelayed({
            if (current === item && item.session == session) finish(item)
        }, timeout)
    }

    private fun finish(item: Item) {
        main.post {
            if (item.session != session || current !== item) return@post
            speaking = false
            current = null
            item.onDone()
            pump()
        }
    }

    private fun drain() {
        val pending = queue.toList()
        queue.clear()
        speaking = false
        current = null
        pending.forEach { item ->
            if (item.session == session) item.onDone()
        }
    }

    private class Item(
        val text: String,
        val onDone: () -> Unit,
        val session: Int,
    )
}
