package com.xiaoai.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class VoskMic(
    private val onText: (String, Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var resetRequested = false
    @Volatile private var dropUntil = 0L
    private var thread: Thread? = null
    private var recorder: AudioRecord? = null

    fun isActive(): Boolean = running

    fun isPaused(): Boolean = paused

    fun start(modelPath: String) {
        if (running) {
            paused = false
            return
        }
        running = true
        paused = false
        thread = Thread({
            try {
                runLoop(modelPath)
            } catch (_: Exception) {
                main.post { onError("语音识别启动失败") }
            }
        }, "xiaoai-vosk").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun pause() {
        paused = true
        resetRequested = true
    }

    fun resume() {
        dropUntil = System.currentTimeMillis() + 350
        paused = false
    }

    fun requestReset() {
        resetRequested = true
    }

    fun stop() {
        running = false
        paused = false
        thread?.join(2000)
        thread = null
    }

    private fun runLoop(modelPath: String) {
        val model = Model(modelPath)
        val recognizer = Recognizer(model, 16000f)
        val minBuffer = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuffer, 8000)
        val buffer = ByteArray(bufferSize)
        val shorts = ShortArray(bufferSize / 2)
        main.post { onReady() }
        try {
            while (running) {
                if (paused) {
                    releaseRecorder()
                    while (running && paused) Thread.sleep(40)
                    continue
                }
                val active = recorder ?: openRecorder(bufferSize).also { recorder = it }
                if (active == null) {
                    main.post { onError("麦克风打不开") }
                    running = false
                    break
                }
                if (active.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    active.startRecording()
                }
                val count = active.read(buffer, 0, buffer.size)
                if (count <= 0) continue
                if (resetRequested) {
                    recognizer.reset()
                    resetRequested = false
                    continue
                }
                if (System.currentTimeMillis() < dropUntil) continue
                val samples = count / 2
                if (samples <= 0) continue
                ByteBuffer.wrap(buffer, 0, samples * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .get(shorts, 0, samples)
                val isFinal = recognizer.acceptWaveForm(shorts, samples)
                val json = if (isFinal) recognizer.result else recognizer.partialResult
                val text = textOf(json, isFinal)
                if (text.isNotBlank()) main.post { onText(text, isFinal) }
            }
        } finally {
            releaseRecorder()
            runCatching { recognizer.close() }
            runCatching { model.close() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(bufferSize: Int): AudioRecord? {
        val sources = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        for (source in sources) {
            try {
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                if (record.state == AudioRecord.STATE_INITIALIZED) return record
                record.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun releaseRecorder() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    private fun textOf(json: String, isFinal: Boolean): String {
        return try {
            val obj = JSONObject(json)
            if (isFinal) obj.optString("text") else obj.optString("partial")
        } catch (_: Exception) {
            ""
        }
    }
}
