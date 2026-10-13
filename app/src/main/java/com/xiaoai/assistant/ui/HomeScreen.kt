package com.xiaoai.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.xiaoai.assistant.data.ChatLine
import com.xiaoai.assistant.data.Phase
import com.xiaoai.assistant.data.UiState
import com.xiaoai.assistant.ui.theme.XiaoAiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onOpenSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onSubmit: (String) -> Unit,
    onToggleListen: () -> Unit,
    onTogglePlay: () -> Unit,
    onStopPlay: () -> Unit,
    onOpenExternal: () -> Unit,
    onRetrySpeech: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Scaffold(
        containerColor = XiaoAiColors.Bg,
        topBar = {
            TopAppBar(
                title = { Text("小爱同学", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = XiaoAiColors.Text,
                    actionIconContentColor = XiaoAiColors.Text,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Orb(state.phase)
            Text(
                text = state.status,
                color = XiaoAiColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = state.caption.ifBlank { " " },
                color = XiaoAiColors.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            TextButton(onClick = onToggleListen, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (state.alwaysListen) "暂停聆听" else "继续聆听")
            }
            if (!state.hasMicPermission) {
                Card(colors = CardDefaults.cardColors(containerColor = XiaoAiColors.Card)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("要用语音叫醒小爱，需要允许麦克风。打字也可以先用。", color = XiaoAiColors.Text)
                        Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 8.dp)) {
                            Text("允许麦克风")
                        }
                    }
                }
            }
            state.speechProgress?.let { progress ->
                Text("正在下载离线语音包，大约 42MB，只下一次", color = XiaoAiColors.Muted, fontSize = 12.sp)
                if (progress < 0f) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
            }
            state.speechError?.let { error ->
                Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
                TextButton(onClick = onRetrySpeech) { Text("重试下载语音包") }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            "试试说「小爱同学，播放晴天」，或者说「小爱同学，你好」。",
                            color = XiaoAiColors.Muted,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(state.messages, key = { it.id }) { line -> Bubble(line) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                listOf("播放晴天", "你好").forEach { sample ->
                    SuggestionChip(onClick = { onSubmit(sample) }, label = { Text(sample) })
                }
            }
            state.nowPlaying?.let { playing ->
                Card(colors = CardDefaults.cardColors(containerColor = XiaoAiColors.Card), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(playing.title, color = XiaoAiColors.Text, fontWeight = FontWeight.SemiBold)
                            val extra = listOf(playing.artist, playing.source).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(if (playing.playing) extra else "$extra · 已暂停或播完", color = XiaoAiColors.Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = onTogglePlay) {
                            Icon(
                                if (playing.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "暂停或继续",
                                tint = XiaoAiColors.Text,
                            )
                        }
                        IconButton(onClick = onStopPlay) {
                            Icon(Icons.Filled.Stop, contentDescription = "停止", tint = XiaoAiColors.Text)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val externalLabel = when {
                state.needExternalApp && state.qqHandoff -> "用 QQ 音乐播放"
                state.needExternalApp && !state.externalQuery.isNullOrBlank() -> "打开音乐应用搜索"
                state.nowPlaying?.fullLength == false -> "用音乐App听完整版"
                else -> null
            }
            if (externalLabel != null) {
                Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) { Text(externalLabel) }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("也可以打字，比如：播放晴天") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (draft.isNotBlank()) {
                            onSubmit(draft)
                            draft = ""
                        }
                    }),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSubmit(draft)
                            draft = ""
                        }
                    },
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "发送", tint = XiaoAiColors.Orange)
                }
            }
        }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    val mine = line.role == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text = line.text,
            color = if (mine) Color(0xFF1A0E00) else XiaoAiColors.Text,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) XiaoAiColors.Orange else XiaoAiColors.Assistant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Orb(phase: Phase) {
    val active = phase == Phase.LISTENING || phase == Phase.AWAKENED || phase == Phase.SPEAKING
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (active) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(if (active) 700 else 1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val color = when (phase) {
        Phase.LISTENING, Phase.AWAKENED -> XiaoAiColors.Orange
        Phase.THINKING -> Color(0xFF4C8DFF)
        Phase.SPEAKING -> Color(0xFFFFB020)
        Phase.ERROR -> Color(0xFFFF5A5A)
        Phase.IDLE -> Color(0xFF3A4150)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(168.dp).scale(pulse), contentAlignment = Alignment.Center) {
            Box(Modifier.size(168.dp).background(color.copy(alpha = 0.18f), CircleShape))
            Box(Modifier.size(120.dp).background(color.copy(alpha = 0.35f), CircleShape))
            Box(
                Modifier
                    .size(78.dp)
                    .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.75f))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
    }
}
