package com.xiaoai.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object XiaoAiColors {
    val Bg = Color(0xFF0B0D12)
    val Card = Color(0xFF171B22)
    val Orange = Color(0xFFFF7A1A)
    val Text = Color(0xFFF4F6FB)
    val Muted = Color(0xFF9AA3B2)
    val Assistant = Color(0xFF222833)
}

private val Dark = darkColorScheme(
    primary = XiaoAiColors.Orange,
    onPrimary = Color(0xFF1A0E00),
    background = XiaoAiColors.Bg,
    surface = XiaoAiColors.Card,
    onBackground = XiaoAiColors.Text,
    onSurface = XiaoAiColors.Text,
)

@Composable
fun XiaoAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
