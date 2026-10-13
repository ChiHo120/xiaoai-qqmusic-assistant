package com.xiaoai.assistant.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

class SongPlayer(
    context: Context,
    private val onPlaying: (Boolean) -> Unit,
    private val onComplete: () -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                holdForSpeech = false
                try {
                    player?.pause()
                } catch (_: Exception) {
                }
                onPlaying(false)
            }
        }
        .build()
    private var player: MediaPlayer? = null
    private var playToken = 0
    private var holdForSpeech = false

    fun play(url: String, onReady: () -> Unit, onError: () -> Unit) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            main.post { onError() }
            return
        }
        val token = ++playToken
        main.post {
            if (token != playToken) return@post
            releasePlayer()
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnPreparedListener {
                if (token != playToken) return@setOnPreparedListener
                audioManager.requestAudioFocus(focusRequest)
                it.start()
                onPlaying(true)
                onReady()
            }
            mp.setOnCompletionListener {
                if (token != playToken) return@setOnCompletionListener
                onPlaying(false)
                onComplete()
            }
            mp.setOnErrorListener { _, _, _ ->
                if (token == playToken) {
                    onPlaying(false)
                    main.post { if (token == playToken) onError() }
                }
                true
            }
            try {
                mp.setDataSource(app, Uri.parse(url), mapOf(
                    "User-Agent" to "XiaoAiAssistant/1.0",
                    "Referer" to "https://y.qq.com/",
                ))
                mp.prepareAsync()
            } catch (_: Exception) {
                main.post { if (token == playToken) onError() }
            }
        }
    }

    fun pause() {
        holdForSpeech = false
        try {
            player?.pause()
        } catch (_: Exception) {
        }
        onPlaying(false)
    }

    fun resume() {
        try {
            player?.start()
            onPlaying(true)
        } catch (_: Exception) {
        }
    }

    fun toggle() {
        if (isPlaying()) pause() else resume()
    }

    fun stop() {
        holdForSpeech = false
        playToken += 1
        releasePlayer()
        onPlaying(false)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun pauseForSpeech() {
        if (isPlaying()) {
            holdForSpeech = true
            try {
                player?.pause()
            } catch (_: Exception) {
            }
        }
    }

    fun resumeAfterSpeech() {
        if (!holdForSpeech) return
        holdForSpeech = false
        try {
            player?.start()
            onPlaying(true)
        } catch (_: Exception) {
        }
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        current.setOnErrorListener(null)
        current.setOnCompletionListener(null)
        current.setOnPreparedListener(null)
        try {
            current.stop()
        } catch (_: Exception) {
        }
        try {
            current.release()
        } catch (_: Exception) {
        }
    }
}
