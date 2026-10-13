package com.xiaoai.assistant.voice

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xiaoai.assistant.MainActivity
import com.xiaoai.assistant.R
import com.xiaoai.assistant.data.SettingsRepository
import com.xiaoai.assistant.logic.AssistantBus
import com.xiaoai.assistant.logic.AssistantCore
import com.xiaoai.assistant.logic.ForegroundHost

class VoiceService : Service() {
    private var keepForeground = true
    private var wakeLock: PowerManager.WakeLock? = null
    private val host = object : ForegroundHost {
        override fun refresh(status: String) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                applyStatus(status)
            } else {
                mainHandler.post { applyStatus(status) }
            }
        }
    }
    private val mainHandler = android.os.Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        AssistantCore.init(this)
        createChannel()
        acquireWake()
        keepForeground = true
        refreshForeground("小爱同学正在聆听")
        AssistantCore.attach(host)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SettingsRepository(this).alwaysListen = false
            AssistantBus.update { it.copy(alwaysListen = false) }
            keepForeground = AssistantCore.isPlaybackActive()
            AssistantCore.stopMic()
            if (!AssistantCore.isPlaybackActive()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            refreshForeground("正在播放")
            return START_STICKY
        }
        keepForeground = true
        val status = AssistantBus.state.value.status.ifBlank { "小爱同学正在聆听" }
        refreshForeground(status)
        if (SettingsRepository(this).alwaysListen && hasMic()) {
            AssistantCore.startMic()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (SettingsRepository(this).alwaysListen && hasMic()) {
            val restart = Intent(this, VoiceService::class.java)
            val pending = PendingIntent.getService(
                this,
                2,
                restart,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            try {
                val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
                alarm.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1500,
                    pending,
                )
            } catch (_: Exception) {
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        keepForeground = false
        AssistantCore.stopMic()
        AssistantCore.detach(host)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyStatus(status: String) {
        if (!keepForeground && !AssistantCore.isPlaybackActive()) return
        refreshForeground(status)
    }

    private fun refreshForeground(status: String) {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(status), foregroundType())
    }

    private fun foregroundType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val mic = SettingsRepository(this).alwaysListen && hasMic()
        val media = AssistantCore.isPlaybackActive()
        var type = 0
        if (mic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (media || (mic && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q)) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
        if (type == 0) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        return type
    }

    private fun buildNotification(status: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("小爱同学")
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_notif, "暂停聆听", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "小爱同学", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWake() {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xiaoai:listen").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    private fun hasMic(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CHANNEL_ID = "xiaoai"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.xiaoai.assistant.STOP"
        private const val ACTION_START = "com.xiaoai.assistant.START"

        fun start(context: Context) {
            val intent = Intent(context, VoiceService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopListen(context: Context) {
            val intent = Intent(context, VoiceService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
