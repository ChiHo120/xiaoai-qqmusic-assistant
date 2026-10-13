package com.xiaoai.assistant.data

import java.util.concurrent.atomic.AtomicLong

private val ids = AtomicLong(System.currentTimeMillis())

enum class Phase {
    IDLE,
    LISTENING,
    AWAKENED,
    THINKING,
    SPEAKING,
    ERROR,
}

data class ChatLine(
    val role: String,
    val text: String,
    val id: Long = ids.incrementAndGet(),
)

data class NowPlaying(
    val title: String,
    val artist: String,
    val source: String,
    val fullLength: Boolean,
    val playing: Boolean,
)

data class UiState(
    val phase: Phase = Phase.IDLE,
    val status: String = "准备中",
    val caption: String = "",
    val messages: List<ChatLine> = emptyList(),
    val nowPlaying: NowPlaying? = null,
    val speechReady: Boolean = false,
    val speechProgress: Float? = null,
    val speechError: String? = null,
    val externalQuery: String? = null,
    val needExternalApp: Boolean = false,
    val qqHandoff: Boolean = false,
    val alwaysListen: Boolean = true,
    val hasMicPermission: Boolean = false,
)

data class Settings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val musicApi: String = "",
    val qqMusicApi: String = "",
    val preferQqMusic: Boolean = true,
    val alwaysListen: Boolean = true,
    val models: List<String> = emptyList(),
    val modelsCachedAt: Long = 0L,
)
