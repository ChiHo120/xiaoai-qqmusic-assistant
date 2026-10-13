package com.xiaoai.assistant.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoai.assistant.data.SettingsRepository
import com.xiaoai.assistant.logic.AssistantBus
import com.xiaoai.assistant.logic.AssistantCore
import com.xiaoai.assistant.media.MusicHandoff
import com.xiaoai.assistant.voice.VoiceService

@Composable
fun AppRoot(onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf("home") }
    val state by AssistantBus.state.collectAsStateWithLifecycle()
    if (screen == "settings") {
        SettingsScreen(state = state, onBack = { screen = "home" })
        return
    }
    HomeScreen(
        state = state,
        onOpenSettings = { screen = "settings" },
        onRequestPermission = onRequestPermission,
        onSubmit = { text ->
            AssistantCore.submit(text)
            val granted = state.hasMicPermission
            if (granted && SettingsRepository(context).alwaysListen) VoiceService.start(context)
        },
        onToggleListen = {
            val next = !state.alwaysListen
            SettingsRepository(context).alwaysListen = next
            AssistantBus.update { it.copy(alwaysListen = next) }
            if (next) {
                if (state.hasMicPermission) VoiceService.start(context) else onRequestPermission()
            } else {
                VoiceService.stopListen(context)
            }
        },
        onTogglePlay = { AssistantCore.togglePlay() },
        onStopPlay = { AssistantCore.stopMusic() },
        onOpenExternal = {
            val query = state.externalQuery ?: return@HomeScreen
            val label = if (state.qqHandoff || SettingsRepository(context).preferQqMusic) {
                MusicHandoff.openQq(context, query) ?: MusicHandoff.open(context, query)
            } else {
                MusicHandoff.open(context, query)
            }
            val tip = if (label == null) "没有找到可用的音乐应用" else "已打开$label"
            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
        },
        onRetrySpeech = { AssistantCore.downloadSpeechModel() },
    )
}
