package com.xiaoai.assistant.logic

import com.xiaoai.assistant.data.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object AssistantBus {
    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState

    fun update(block: (UiState) -> UiState) {
        mutableState.update(block)
    }
}
