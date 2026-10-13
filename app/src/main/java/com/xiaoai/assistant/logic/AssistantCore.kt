package com.xiaoai.assistant.logic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.xiaoai.assistant.data.ChatLine
import com.xiaoai.assistant.data.NowPlaying
import com.xiaoai.assistant.data.Phase
import com.xiaoai.assistant.data.SettingsRepository
import com.xiaoai.assistant.media.MusicHandoff
import com.xiaoai.assistant.media.SongPlayer
import com.xiaoai.assistant.net.AiClient
import com.xiaoai.assistant.net.MusicClient
import com.xiaoai.assistant.net.QqMusicClient
import com.xiaoai.assistant.net.Track
import com.xiaoai.assistant.voice.Speaker
import com.xiaoai.assistant.voice.SystemMic
import com.xiaoai.assistant.voice.VoiceService
import com.xiaoai.assistant.voice.VoskMic
import com.xiaoai.assistant.voice.VoskModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ForegroundHost {
    fun refresh(status: String)
}

object AssistantCore {
    private enum class Mode { WAKE, COMMAND }

    private lateinit var app: Context
    private lateinit var repo: SettingsRepository
    private lateinit var speaker: Speaker
    private lateinit var player: SongPlayer
    private lateinit var vosk: VoskMic
    private lateinit var system: SystemMic

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "assistant error", error)
            if (initialized) setStatus(Phase.ERROR, error.message ?: "出错了")
        },
    )

    @Volatile private var initialized = false
    @Volatile private var downloading = false
    @Volatile var host: ForegroundHost? = null
    private var playbackActive = false
    private var musicJob: Job? = null
    private var musicRequest = 0
    private var mode = Mode.WAKE
    private var pendingCommand: String? = null
    private var pendingChangedAt = 0L
    private var wakeLocked = false
    private var ignoreMic = false
    private var turnActive = false
    private var commandDeadline = 0L

    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        repo = SettingsRepository(app)
        player = SongPlayer(
            app,
            onPlaying = { playing -> updatePlaying(playing) },
            onComplete = {
                playbackActive = false
                host?.refresh("播放结束")
            },
        )
        speaker = Speaker(app) { talking ->
            if (talking) player.pauseForSpeech() else player.resumeAfterSpeech()
        }
        vosk = VoskMic(
            onText = { text, isFinal -> onVoiceTranscript(text, isFinal) },
            onError = { message -> setStatus(Phase.ERROR, message) },
            onReady = {
                AssistantBus.update { it.copy(speechReady = true) }
                if (!turnActive) setStatus(Phase.LISTENING, WAKE_HINT)
            },
        )
        system = SystemMic(app)
        val history = repo.loadHistory()
        AssistantBus.update {
            it.copy(
                messages = if (it.messages.isEmpty()) history else it.messages,
                alwaysListen = repo.alwaysListen,
                speechReady = VoskModelStore.locate(app) != null,
                hasMicPermission = hasMic(),
            )
        }
        initialized = true
        scope.launch { ticker() }
    }

    fun attach(host: ForegroundHost) {
        this.host = host
    }

    fun detach(host: ForegroundHost) {
        if (this.host === host) this.host = null
    }

    fun isPlaybackActive(): Boolean = playbackActive

    fun submit(text: String) {
        ensure()
        val shown = text.trim()
        if (shown.isBlank()) return
        turnActive = true
        ignoreMic = true
        pauseMic()
        speaker.stop()
        dispatch(shown, recordUser = true)
    }

    fun startMic() {
        ensure()
        if (turnActive) return
        if (!hasMic()) {
            AssistantBus.update { it.copy(hasMicPermission = false) }
            setStatus(Phase.ERROR, "需要麦克风权限")
            return
        }
        AssistantBus.update { it.copy(hasMicPermission = true) }
        if (VoskModelStore.locate(app) != null) {
            system.stop()
            startVoskEngine()
        } else if (system.available()) {
            ignoreMic = false
            mode = Mode.WAKE
            system.startLoop { text, isFinal -> onVoiceTranscript(text, isFinal) }
            setStatus(Phase.LISTENING, WAKE_HINT)
            downloadSpeechModel()
        } else {
            setStatus(Phase.THINKING, "正在下载离线语音包")
            downloadSpeechModel()
        }
    }

    fun stopMic() {
        if (!initialized) return
        turnActive = false
        ignoreMic = true
        mode = Mode.WAKE
        pendingCommand = null
        system.stop()
        vosk.stop()
        setStatus(Phase.IDLE, "聆听已关闭")
    }

    fun togglePlay() {
        ensure()
        player.toggle()
    }

    fun stopMusic() {
        ensure()
        stopPlayback()
    }

    fun downloadSpeechModel() {
        ensure()
        if (VoskModelStore.locate(app) != null) {
            AssistantBus.update { it.copy(speechReady = true, speechProgress = null, speechError = null) }
            return
        }
        if (downloading) return
        downloading = true
        AssistantBus.update { it.copy(speechProgress = -1f, speechError = null) }
        scope.launch(Dispatchers.IO) {
            try {
                VoskModelStore.download(app) { progress ->
                    AssistantBus.update { it.copy(speechProgress = progress, speechError = null) }
                }
                withContext(Dispatchers.Main) {
                    downloading = false
                    AssistantBus.update { it.copy(speechReady = true, speechProgress = null, speechError = null) }
                    onModelReady()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                downloading = false
                withContext(Dispatchers.Main) {
                    AssistantBus.update {
                        it.copy(speechProgress = null, speechError = error.message ?: "语音包下载失败")
                    }
                }
            }
        }
    }

    private fun onModelReady() {
        if (!repo.alwaysListen || !hasMic() || turnActive || speaker.isSpeaking()) return
        if (!vosk.isActive()) {
            system.stop()
            startVoskEngine()
        }
    }

    private fun startVoskEngine() {
        val dir = VoskModelStore.locate(app) ?: return
        ignoreMic = false
        mode = Mode.WAKE
        if (vosk.isActive()) {
            vosk.resume()
            setStatus(Phase.LISTENING, WAKE_HINT)
        } else {
            setStatus(Phase.THINKING, "正在加载语音")
            vosk.start(dir.absolutePath)
        }
    }

    private suspend fun ticker() {
        while (true) {
            delay(200)
            if (mode == Mode.WAKE) flushWake(force = false)
            if (mode == Mode.COMMAND && !system.isBusy() && !ignoreMic && System.currentTimeMillis() > commandDeadline) {
                mode = Mode.WAKE
                setStatus(Phase.LISTENING, WAKE_HINT)
            }
        }
    }

    private fun onVoiceTranscript(text: String, isFinal: Boolean) {
        if (!initialized || ignoreMic || !repo.alwaysListen) return
        val normalized = CommandRouter.normalize(text)
        if (normalized.isBlank()) return
        AssistantBus.update { it.copy(caption = normalized.take(42)) }
        if (mode == Mode.COMMAND) {
            if (!isFinal) return
            mode = Mode.WAKE
            val command = CommandRouter.extractAfterWake(normalized) ?: normalized
            if (command.isBlank()) return
            ignoreMic = true
            pauseMic()
            dispatch(command, recordUser = true)
            return
        }
        if (wakeLocked) return
        val remainder = CommandRouter.extractAfterWake(text) ?: return
        if (remainder != pendingCommand) {
            pendingCommand = remainder
            pendingChangedAt = System.currentTimeMillis()
        }
        if (isFinal) flushWake(force = true)
    }

    private fun flushWake(force: Boolean) {
        val command = pendingCommand ?: return
        if (!force && System.currentTimeMillis() - pendingChangedAt < 700) return
        pendingCommand = null
        if (wakeLocked) return
        wakeLocked = true
        scope.launch {
            delay(2500)
            wakeLocked = false
        }
        beginWake(command)
    }

    private fun beginWake(command: String) {
        turnActive = true
        ignoreMic = true
        mode = Mode.WAKE
        pauseMic()
        speaker.stop()
        vosk.requestReset()
        if (command.isNotBlank()) addMessage("user", command)
        addMessage("assistant", "我在")
        setStatus(Phase.AWAKENED, "我在")
        speaker.speak("我在") {
            if (command.isBlank()) listenNextCommand() else dispatch(command, recordUser = false)
        }
    }

    private fun listenNextCommand() {
        mode = Mode.COMMAND
        commandDeadline = System.currentTimeMillis() + 9000
        setStatus(Phase.LISTENING, "我在，请说")
        if (system.available()) {
            system.recognizeOnce { text ->
                mode = Mode.WAKE
                if (text.isNullOrBlank()) speakResume("我没听清，再说一次吧")
                else dispatch(text, recordUser = true)
            }
        } else {
            ignoreMic = false
            if (vosk.isActive()) vosk.resume() else startVoskEngine()
        }
    }

    private fun dispatch(raw: String, recordUser: Boolean) {
        turnActive = true
        val command = CommandRouter.parse(raw)
        if (recordUser && raw.isNotBlank() && command !is UserCommand.Empty) {
            addMessage("user", raw.trim())
        }
        when (command) {
            UserCommand.Empty -> listenNextCommand()
            is UserCommand.Say -> speakResume(command.text)
            UserCommand.PauseMusic -> {
                player.pause()
                speakResume("暂停了")
            }
            UserCommand.ResumeMusic -> {
                player.resume()
                speakResume("继续播放")
            }
            UserCommand.StopMusic -> {
                stopPlayback()
                speakResume("已经停止播放")
            }
            UserCommand.StopListen -> {
                repo.alwaysListen = false
                AssistantBus.update { it.copy(alwaysListen = false) }
                addMessage("assistant", "好，那我先不听了")
                speaker.speak("好，那我先不听了") {
                    turnActive = false
                    VoiceService.stopListen(app)
                }
            }
            UserCommand.StartListen -> {
                repo.alwaysListen = true
                AssistantBus.update { it.copy(alwaysListen = true) }
                turnActive = false
                VoiceService.start(app)
                speakResume("我开始听了")
            }
            UserCommand.ClearChat -> {
                AssistantBus.update { it.copy(messages = emptyList()) }
                repo.saveHistory(emptyList())
                speakResume("对话清掉了")
            }
            is UserCommand.Play -> searchAndPlay(command.query)
            is UserCommand.Chat -> askAi((CommandRouter.extractAfterWake(raw) ?: raw).trim())
        }
    }

    private fun searchAndPlay(query: MusicQuery) {
        musicJob?.cancel()
        val request = ++musicRequest
        player.stop()
        AssistantBus.update { it.copy(nowPlaying = null) }
        playbackActive = true
        host?.refresh("正在找歌")
        AssistantBus.update {
            it.copy(
                phase = Phase.THINKING,
                status = "正在找「${query.spokenTitle}」",
                externalQuery = query.searchText,
                needExternalApp = false,
                qqHandoff = false,
            )
        }
        musicJob = scope.launch {
            val settings = repo.load()
            if (settings.qqMusicApi.isNotBlank()) {
                val qqTracks = try {
                    withContext(Dispatchers.IO) { QqMusicClient.search(settings.qqMusicApi, query) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    addMessage("assistant", "QQ 音乐接入失败，请检查服务地址、网络或登录状态。")
                    emptyList()
                }
                if (request != musicRequest) return@launch
                if (qqTracks.isNotEmpty()) {
                    playAt(qqTracks, 0, query, request)
                } else {
                    addMessage("assistant", "没有取得这首歌的授权音频，将尝试音乐应用。")
                    onNoStream(query)
                }
                return@launch
            }
            if (settings.preferQqMusic && MusicHandoff.qqInstalled(app)) {
                openQq(query)
                return@launch
            }
            val tracks = try {
                MusicClient.findTracks(settings.musicApi, query)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
            if (request == musicRequest) playAt(tracks, 0, query, request)
        }
    }

    private fun openQq(query: MusicQuery) {
        playbackActive = false
        val opened = runCatching { MusicHandoff.openQq(app, query.searchText) }.getOrNull()
        AssistantBus.update {
            it.copy(
                nowPlaying = null,
                externalQuery = query.searchText,
                needExternalApp = opened == null,
                qqHandoff = true,
            )
        }
        host?.refresh(if (opened != null) "已打开 QQ 音乐" else "点按用 QQ 音乐播放")
        val title = query.spokenTitle
        val text = if (opened != null) {
            "好，已在 QQ 音乐打开$title。用你的会员就能听完整版。"
        } else {
            "QQ 音乐已经装好了。点一下屏幕上的按钮，用会员听完整版。"
        }
        speakResume(text)
    }

    private fun playAt(tracks: List<Track>, index: Int, query: MusicQuery, request: Int) {
        if (request != musicRequest) return
        if (index >= tracks.size) {
            onNoStream(query)
            return
        }
        val track = tracks[index]
        setStatus(Phase.THINKING, "正在连接「${track.title}」")
        player.play(
            url = track.url,
            onReady = {
                if (request != musicRequest) return@play
                playbackActive = true
                AssistantBus.update {
                    it.copy(
                        nowPlaying = NowPlaying(
                            title = track.title,
                            artist = track.artist,
                            source = track.source,
                            fullLength = track.fullLength,
                            playing = true,
                        ),
                        externalQuery = query.searchText,
                        needExternalApp = false,
                        qqHandoff = false,
                    )
                }
                host?.refresh("正在播放${track.title}")
                speakResume(track.speakLine())
            },
            onError = { playAt(tracks, index + 1, query, request) },
        )
    }

    private fun onNoStream(query: MusicQuery) {
        playbackActive = false
        val opened = runCatching { MusicHandoff.open(app, query.searchText) }.getOrNull()
        AssistantBus.update {
            it.copy(
                nowPlaying = null,
                externalQuery = query.searchText,
                needExternalApp = opened == null,
                qqHandoff = false,
            )
        }
        host?.refresh("点按用音乐应用搜索")
        val text = if (opened != null) {
            "应用里没找到可播放的音频，我帮你打开了$opened。"
        } else {
            "应用里没找到这首歌的可播放音频。点一下屏幕上的按钮，用音乐应用搜索。"
        }
        speakResume(text)
    }

    private fun askAi(text: String) {
        if (text.isBlank()) {
            finishTurn()
            return
        }
        val settings = repo.load()
        if (settings.baseUrl.isBlank() || settings.model.isBlank()) {
            speakResume("聊天要先在设置里填写接口地址和模型。放歌现在就可以直接说。")
            return
        }
        setStatus(Phase.THINKING, "正在想")
        val prior = historyFor(text)
        scope.launch {
            val reply = try {
                withContext(Dispatchers.IO) {
                    AiClient.chat(settings.baseUrl, settings.apiKey, settings.model, prior, text)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                "接口没调通。${error.message?.take(40) ?: "请检查地址和模型"}"
            }
            speakResume(reply)
        }
    }

    private fun historyFor(currentUser: String): List<Pair<String, String>> {
        val items = AssistantBus.state.value.messages
            .filter { (it.role == "user" || it.role == "assistant") && it.text != "我在" }
            .toMutableList()
        val last = items.lastOrNull()
        if (last != null && last.role == "user" && last.text == currentUser) {
            items.removeAt(items.lastIndex)
        }
        return items.takeLast(8).map { it.role to it.text }
    }

    private fun speakResume(text: String) {
        val spoken = forSpeech(text)
        if (spoken.isBlank()) {
            finishTurn()
            return
        }
        addMessage("assistant", text.trim())
        setStatus(Phase.SPEAKING, "正在说")
        speaker.speak(spoken) { finishTurn() }
    }

    private fun finishTurn() {
        turnActive = false
        if (repo.alwaysListen && hasMic()) {
            ignoreMic = false
            mode = Mode.WAKE
            val model = VoskModelStore.locate(app)
            if (model != null) {
                if (vosk.isActive()) vosk.resume() else vosk.start(model.absolutePath)
            } else if (system.available()) {
                system.startLoop { text, isFinal -> onVoiceTranscript(text, isFinal) }
            }
            AssistantBus.update { it.copy(caption = "") }
            setStatus(Phase.LISTENING, WAKE_HINT)
        } else {
            ignoreMic = true
            pauseMic()
            setStatus(Phase.IDLE, if (hasMic()) "聆听已关闭" else "需要麦克风权限")
        }
    }

    private fun pauseMic() {
        vosk.pause()
        system.stop()
    }

    private fun stopPlayback() {
        musicJob?.cancel()
        musicRequest += 1
        playbackActive = false
        player.stop()
        AssistantBus.update { it.copy(nowPlaying = null) }
        host?.refresh(AssistantBus.state.value.status)
    }

    private fun updatePlaying(playing: Boolean) {
        AssistantBus.update { state -> state.copy(nowPlaying = state.nowPlaying?.copy(playing = playing)) }
        host?.refresh(AssistantBus.state.value.status)
    }

    private fun addMessage(role: String, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        AssistantBus.update { state ->
            state.copy(messages = (state.messages + ChatLine(role, clean)).takeLast(40))
        }
        repo.saveHistory(AssistantBus.state.value.messages)
    }

    private fun setStatus(phase: Phase, status: String) {
        AssistantBus.update { it.copy(phase = phase, status = status) }
        host?.refresh(status)
    }

    private fun forSpeech(text: String): String {
        val clean = text.replace("**", "").replace("`", "").replace("#", "").trim()
        return if (clean.length <= 180) clean else clean.take(180)
    }

    private fun hasMic(): Boolean {
        return ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensure() {
        check(initialized) { "助手还没初始化" }
    }

    private const val TAG = "XiaoAi"
    private const val WAKE_HINT = "说「小爱同学」叫醒我"
}
