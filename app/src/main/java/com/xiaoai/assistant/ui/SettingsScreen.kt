package com.xiaoai.assistant.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoai.assistant.data.SettingsRepository
import com.xiaoai.assistant.data.UiState
import com.xiaoai.assistant.logic.AssistantBus
import com.xiaoai.assistant.logic.AssistantCore
import com.xiaoai.assistant.net.AiClient
import com.xiaoai.assistant.net.QqMusicClient
import com.xiaoai.assistant.logic.MusicQuery
import com.xiaoai.assistant.ui.theme.XiaoAiColors
import com.xiaoai.assistant.voice.VoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val initial = remember { repo.load() }
    var base by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var apiKey by rememberSaveable { mutableStateOf(initial.apiKey) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var musicApi by rememberSaveable { mutableStateOf(initial.musicApi) }
    var qqMusicApi by rememberSaveable { mutableStateOf(initial.qqMusicApi) }
    var testingMusic by remember { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var models by remember { mutableStateOf(initial.models) }
    var cachedAt by rememberSaveable { mutableStateOf(initial.modelsCachedAt) }
    var fetching by rememberSaveable { mutableStateOf(false) }
    var testing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun save() {
        repo.save(base, apiKey, model, musicApi)
        base = repo.load().baseUrl
        message = "已保存在这台手机上"
    }

    Scaffold(
        containerColor = XiaoAiColors.Bg,
        topBar = {
            TopAppBar(
                title = { Text("接入 AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = XiaoAiColors.Text,
                    navigationIconContentColor = XiaoAiColors.Text,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "填写 OpenAI 兼容接口。DeepSeek、月之暗面、硅基流动、Ollama 都可以，地址一般以 /v1 结尾。密钥和模型只存在这台手机里。",
                color = XiaoAiColors.Muted,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                value = base,
                onValueChange = { base = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                singleLine = true,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 密钥") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "显示密钥",
                        )
                    }
                },
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型") },
                placeholder = { Text("获取列表，或自己填写模型名") },
                singleLine = true,
            )
            if (models.isNotEmpty()) {
                val ordered = listOfNotNull(model.takeIf { it.isNotBlank() && it in models }) + models.filter { it != model }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ordered.take(24).forEach { name ->
                        FilterChip(
                            selected = name == model,
                            onClick = {
                                model = name
                                repo.save(base, apiKey, name, musicApi)
                                message = "已选择并保存 $name"
                            },
                            label = { Text(name, maxLines = 1) },
                        )
                    }
                }
            }
            if (cachedAt > 0) {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(cachedAt))
                Text("模型已缓存在手机：$time，下次打开可直接用", color = XiaoAiColors.Muted, fontSize = 12.sp)
            }
            Button(
                onClick = {
                    fetching = true
                    message = ""
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { AiClient.fetchModels(base, apiKey) }
                            val chosen = model.ifBlank { result.models.firstOrNull().orEmpty() }
                            repo.save(result.baseUrl, apiKey, chosen, musicApi)
                            repo.saveModels(result.baseUrl, result.models)
                            base = result.baseUrl
                            model = chosen
                            models = result.models
                            cachedAt = System.currentTimeMillis()
                            message = "已缓存 ${result.models.size} 个模型"
                        } catch (error: Exception) {
                            message = error.message ?: "获取失败，仍可使用上次缓存"
                        } finally {
                            fetching = false
                        }
                    }
                },
                enabled = !fetching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (fetching) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = Color(0xFF1A0E00))
                } else {
                    Text("获取并缓存模型")
                }
            }
            OutlinedButton(
                onClick = {
                    save()
                    testing = true
                    scope.launch {
                        try {
                            val reply = withContext(Dispatchers.IO) {
                                AiClient.chat(repo.load().baseUrl, apiKey.trim(), model.trim(), emptyList(), "请只回复：已接通")
                            }
                            message = "测试成功：$reply"
                        } catch (error: Exception) {
                            message = error.message ?: "测试失败"
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (testing) "正在测试…" else "保存并测试对话")
            }
            TextButton(onClick = {
                repo.clearModels()
                models = emptyList()
                cachedAt = 0
                message = "模型缓存已清除"
            }) { Text("清除模型缓存") }
            if (message.isNotBlank()) Text(message, color = XiaoAiColors.Text)
            Spacer(Modifier.height(8.dp))
            Text("音乐", style = MaterialTheme.typography.titleMedium, color = XiaoAiColors.Text)
            OutlinedTextField(
                value = qqMusicApi,
                onValueChange = { qqMusicApi = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("QQMusicApi 服务地址") },
                placeholder = { Text("http://192.168.1.10:8080") },
                singleLine = true,
            )
            Text(
                "填写后，点歌优先在本应用内播放。手机与服务电脑需网络互通；完整版取决于服务账号的登录和歌曲权限。清空地址可恢复原来的点歌方式。",
                color = XiaoAiColors.Muted,
                fontSize = 13.sp,
            )
            Button(onClick = {
                try {
                    repo.saveQqMusicApi(qqMusicApi)
                    qqMusicApi = repo.load().qqMusicApi
                    message = "已保存 QQMusicApi 地址"
                } catch (error: IllegalArgumentException) {
                    message = error.message.orEmpty()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("保存 QQ 音乐接入") }
            OutlinedButton(onClick = {
                testingMusic = true
                scope.launch {
                    try {
                        val tracks = withContext(Dispatchers.IO) {
                            QqMusicClient.search(qqMusicApi, MusicQuery("晴天", "晴天 周杰伦"))
                        }
                        message = if (tracks.isEmpty()) "未取得测试歌曲的授权链接，请检查登录与歌曲权限"
                        else "接入成功：${tracks.first().title}，可以点歌播放"
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        message = "QQ 音乐测试失败：${error.message?.take(100)}"
                    } finally {
                        testingMusic = false
                    }
                }
            }, enabled = !testingMusic && qqMusicApi.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (testingMusic) "正在测试…" else "测试 QQ 音乐接入")
            }
            Text(
                "未填写 QQMusicApi 地址时，下方开关决定是否优先打开官方 QQ 音乐。",
                color = XiaoAiColors.Muted,
                fontSize = 13.sp,
            )
            var preferQq by rememberSaveable { mutableStateOf(initial.preferQqMusic) }
            RowSwitch(
                checked = preferQq,
                label = "点歌优先打开 QQ 音乐",
                onChecked = { on ->
                    preferQq = on
                    repo.preferQqMusic = on
                },
            )
            Text(
                "未配置 QQMusicApi 且关掉开关时，会在应用里试听公开曲库，也可以填自己的音乐接口。",
                color = XiaoAiColors.Muted,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                value = musicApi,
                onValueChange = { musicApi = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义音乐接口，可选") },
                placeholder = { Text("https://example.com/search?q={q}") },
                singleLine = true,
            )
            Text(
                "返回 JSON，例如 {\"tracks\":[{\"title\":\"晴天\",\"artist\":\"周杰伦\",\"url\":\"https://...mp3\"}]}",
                color = XiaoAiColors.Muted,
                fontSize = 12.sp,
            )
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("保存音乐接口") }
            Spacer(Modifier.height(8.dp))
            Text("始终聆听", style = MaterialTheme.typography.titleMedium, color = XiaoAiColors.Text)
            RowSwitch(
                checked = state.alwaysListen,
                onChecked = { on ->
                    SettingsRepository(context).alwaysListen = on
                    AssistantBus.update { it.copy(alwaysListen = on) }
                    if (on && state.hasMicPermission) VoiceService.start(context) else if (!on) VoiceService.stopListen(context)
                },
            )
            state.speechProgress?.let { progress ->
                Text("离线语音包下载中", color = XiaoAiColors.Muted, fontSize = 12.sp)
                if (progress < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                if (state.speechReady) "离线语音包已在手机里，下次打开不用重新下载。" else "第一次使用会下载约 42MB 中文语音包，用来离线听「小爱同学」。",
                color = XiaoAiColors.Muted,
                fontSize = 13.sp,
            )
            state.speechError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            OutlinedButton(onClick = { AssistantCore.downloadSpeechModel() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.speechReady) "语音包已就绪" else "下载离线语音包")
            }
            OutlinedButton(onClick = { openBattery(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("允许后台运行")
            }
            OutlinedButton(onClick = { openAutoStart(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("打开自启动设置")
            }
            Text(
                "想一直能喊醒，请允许麦克风、通知、后台运行，并在手机管家里打开自启动、锁定最近任务。国产手机杀掉后台后，需要重新打开一次本应用。",
                color = XiaoAiColors.Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RowSwitch(checked: Boolean, onChecked: (Boolean) -> Unit, label: String = "屏幕暗着也听「小爱同学」") {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, color = XiaoAiColors.Text)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun openBattery(context: android.content.Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(context, "请到系统设置里允许后台运行", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openAutoStart(context: android.content.Context) {
    val targets = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    )
    for (component in targets) {
        try {
            context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(context, "请到手机管家里找到自启动，允许小爱同学", Toast.LENGTH_LONG).show()
}
