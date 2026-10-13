package com.xiaoai.assistant.media

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.app.SearchManager
import java.net.URLEncoder

object MusicHandoff {
    private data class MusicApp(
        val label: String,
        val pkg: String,
        val uris: (String) -> List<String>,
    )

    private val apps = listOf(
        MusicApp("网易云音乐", "com.netease.cloudmusic") { query ->
            listOf("orpheus://search?keyword=${enc(query)}")
        },
        MusicApp("QQ音乐", "com.tencent.qqmusic") { query ->
            val payload = enc("""{"type":"0","query":"${query.replace("\"", "")}"}""")
            listOf(
                "qqmusic://qq.com/ui/search?p=$payload",
                "qqmusic://qq.com/search?key=${enc(query)}",
            )
        },
        MusicApp("QQ音乐", "com.tencent.qqmusiclite") { query ->
            listOf("qqmusic://qq.com/search?key=${enc(query)}")
        },
        MusicApp("汽水音乐", "com.luna.music") { query ->
            listOf("luna://search?keyword=${enc(query)}")
        },
        MusicApp("酷狗音乐", "com.kugou.android") { query ->
            listOf("kugou://search?keyword=${enc(query)}")
        },
        MusicApp("酷我音乐", "cn.kuwo.player") { query ->
            listOf("kwapp://search?key=${enc(query)}")
        },
        MusicApp("小米音乐", "com.miui.player") { query ->
            listOf("miui-music://search?q=${enc(query)}")
        },
        MusicApp("Spotify", "com.spotify.music") { query ->
            listOf("spotify:search:${enc(query)}")
        },
        MusicApp("YouTube Music", "com.google.android.apps.youtube.music") { query ->
            listOf("https://music.youtube.com/search?q=${enc(query)}")
        },
    )

    private val qqPackages = setOf("com.tencent.qqmusic", "com.tencent.qqmusiclite")
    private val qqApps: List<MusicApp>
        get() = apps.filter { it.pkg in qqPackages }

    fun qqInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return qqApps.any { installed(pm, it.pkg) }
    }

    fun openQq(context: Context, query: String): String? = openApps(context, query, qqApps)

    fun open(context: Context, query: String): String? {
        val opened = openApps(context, query, apps)
        if (opened != null) return opened
        val generic = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
        }
        return if (launch(context, generic)) "音乐应用" else null
    }

    private fun openApps(context: Context, query: String, targets: List<MusicApp>): String? {
        val pm = context.packageManager
        for (app in targets) {
            if (!installed(pm, app.pkg)) continue
            for (uri in app.uris(query)) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(app.pkg)
                if (launch(context, intent)) return app.label
            }
            val launchIntent = pm.getLaunchIntentForPackage(app.pkg) ?: continue
            if (launch(context, launchIntent)) return app.label
        }
        return null
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            false
        } catch (error: SecurityException) {
            false
        } catch (error: RuntimeException) {
            val blocked = error.message?.contains("Background", ignoreCase = true) == true
            if (blocked) false else false
        }
    }

    private fun installed(pm: PackageManager, pkg: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
