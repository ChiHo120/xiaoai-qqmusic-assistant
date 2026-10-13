package com.xiaoai.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.xiaoai.assistant.data.SettingsRepository
import com.xiaoai.assistant.logic.AssistantBus
import com.xiaoai.assistant.logic.AssistantCore
import com.xiaoai.assistant.ui.AppRoot
import com.xiaoai.assistant.ui.theme.XiaoAiTheme
import com.xiaoai.assistant.voice.VoiceService

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        syncPermission()
        maybeStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AssistantCore.init(this)
        setContent {
            XiaoAiTheme {
                AppRoot(onRequestPermission = { ask() })
            }
        }
        ask()
    }

    override fun onResume() {
        super.onResume()
        syncPermission()
        maybeStart()
    }

    private fun ask() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        permissions.launch(needed.toTypedArray())
    }

    private fun syncPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        AssistantBus.update { it.copy(hasMicPermission = granted) }
    }

    private fun maybeStart() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted && SettingsRepository(this).alwaysListen) {
            VoiceService.start(this)
        }
    }
}
