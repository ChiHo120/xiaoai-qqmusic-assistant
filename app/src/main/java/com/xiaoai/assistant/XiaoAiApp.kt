package com.xiaoai.assistant

import android.app.Application
import com.xiaoai.assistant.logic.AssistantCore

class XiaoAiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AssistantCore.init(this)
    }
}
