package com.xiaoai.assistant.logic

data class MusicQuery(
    val spokenTitle: String,
    val searchText: String,
)

sealed class UserCommand {
    data object Empty : UserCommand()
    data class Say(val text: String) : UserCommand()
    data object PauseMusic : UserCommand()
    data object ResumeMusic : UserCommand()
    data object StopMusic : UserCommand()
    data object StopListen : UserCommand()
    data object StartListen : UserCommand()
    data object ClearChat : UserCommand()
    data class Play(val query: MusicQuery) : UserCommand()
    data class Chat(val text: String) : UserCommand()
}

object CommandRouter {
    private val wakeAliases = listOf("小爱同学", "小艾同学", "晓爱同学")
    private val tail = Regex("(可以吗|好不好|行不行|吧|呀|啊|呢|哦|嘛|呗)+$")
    private val musicPrefixes = listOf(
        "我想放一首",
        "我想听一首",
        "帮我放一首",
        "给我放一首",
        "请放一首",
        "播放一首",
        "放一首",
        "来一首",
        "唱一首",
        "我想听",
        "想听一首",
        "想听",
        "帮我播放一首",
        "给我播放一首",
        "给我播放",
        "我想放",
        "帮我播放",
        "帮我放",
        "给我放",
        "请播放",
        "播放",
        "来首",
        "放首",
        "放歌",
        "听一首",
        "听歌",
    )

    fun normalize(raw: String): String {
        return raw.replace(Regex("[\\s,，.。!！?？、:：;；~～\"'“”‘’]"), "")
    }

    fun extractAfterWake(raw: String): String? {
        val text = normalize(raw)
        var index = -1
        var aliasLength = 0
        for (alias in wakeAliases) {
            val found = text.indexOf(alias)
            if (found >= 0 && (index < 0 || found < index || (found == index && alias.length > aliasLength))) {
                index = found
                aliasLength = alias.length
            }
        }
        if (index < 0) return null
        return text.substring(index + aliasLength)
    }

    fun parse(raw: String): UserCommand {
        val woken = extractAfterWake(raw)
        val text = stripTail(woken ?: normalize(raw))
        if (text.isEmpty()) return UserCommand.Empty
        local(text)?.let { return it }
        music(text)?.let { return it }
        return UserCommand.Chat(text)
    }

    fun stripTail(value: String): String = value.replace(tail, "")

    private fun local(text: String): UserCommand? = when (text) {
        "你好", "在吗", "你在吗", "嗨", "哈喽", "hello", "hi" -> UserCommand.Say("有什么可以帮你？")
        "你是谁", "你叫什么", "你叫什么名字" -> UserCommand.Say("我是小爱同学。")
        "谢谢", "谢谢你", "感谢" -> UserCommand.Say("不客气。")
        "暂停", "暂停播放", "停一下", "暂停一下" -> UserCommand.PauseMusic
        "继续", "继续播放", "恢复播放" -> UserCommand.ResumeMusic
        "停止播放", "别放了", "关闭音乐", "停止音乐" -> UserCommand.StopMusic
        "关闭聆听", "别听了", "停止聆听" -> UserCommand.StopListen
        "打开聆听", "开始聆听" -> UserCommand.StartListen
        "清空对话", "清除对话" -> UserCommand.ClearChat
        "下一首", "上一首", "切歌" -> UserCommand.Say("你直接说歌名就行，比如播放晴天。")
        else -> null
    }

    private fun music(text: String): UserCommand? {
        val prefix = musicPrefixes.firstOrNull { text.startsWith(it) } ?: return null
        val body = stripTail(text.removePrefix(prefix))
        if (body.isEmpty() || body in setOf("歌", "音乐", "歌曲", "首歌", "一首歌")) {
            return UserCommand.Say("想听哪一首？可以说播放晴天。")
        }
        return UserCommand.Play(toQuery(body))
    }

    fun toQuery(body: String): MusicQuery {
        val index = body.indexOf('的')
        if (index in 2..8) {
            val right = body.length - index - 1
            if (right in 1..16) {
                val artist = body.substring(0, index)
                val title = body.substring(index + 1)
                if (title in setOf("歌", "音乐", "歌曲")) {
                    return MusicQuery(spokenTitle = artist, searchText = artist)
                }
                return MusicQuery(spokenTitle = title, searchText = "$title $artist")
            }
        }
        return MusicQuery(spokenTitle = body, searchText = body)
    }
}
