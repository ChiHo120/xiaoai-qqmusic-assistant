package com.xiaoai.assistant.net

import android.util.Log
import com.xiaoai.assistant.logic.MusicQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

data class Track(
    val title: String,
    val artist: String,
    val url: String,
    val source: String,
    val fullLength: Boolean,
    val priority: Int,
) {
    fun speakLine(): String = buildString {
        append("正在播放")
        append(title)
        if (artist.isNotBlank()) {
            append("，")
            append(artist)
        }
        if (!fullLength) append("。这是大约30秒的官方试听")
    }
}

object MusicClient {
    suspend fun findTracks(musicApi: String, query: MusicQuery): List<Track> = supervisorScope {
        val jobs = listOf(
            async { guarded(8000) { searchCustom(musicApi, query) } },
            async { guarded(8000) { searchDeezerSmart(query) } },
            async { guarded(8000) { searchItunes(query.searchText) } },
            async { guarded(12000) { searchArchive(query) } },
        )
        jobs.mapNotNull { it.await() }.sortedBy { it.priority }
    }

    private suspend fun guarded(timeoutMs: Long, block: () -> Track?): Track? {
        return try {
            withTimeout(timeoutMs) { withContext(Dispatchers.IO) { block() } }
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            Log.w(TAG, "music source failed: ${error.message}")
            null
        }
    }

    private fun searchCustom(musicApi: String, query: MusicQuery): Track? {
        if (musicApi.isBlank()) return null
        val template = musicApi.trim()
        if (!template.startsWith("http://") && !template.startsWith("https://")) return null
        val encoded = URLEncoder.encode(query.searchText, "UTF-8")
        val url = if (template.contains("{q}")) {
            template.replace("{q}", encoded)
        } else {
            val join = if (template.contains("?")) "&" else "?"
            "$template${join}q=$encoded"
        }
        return parseTracks(Http.get(url, timeoutMs = 8000), "自定义接口", 0, fullLength = true).firstOrNull()
    }

    private fun searchDeezerSmart(query: MusicQuery): Track? {
        searchDeezer(query.searchText)?.let { return it }
        if (query.searchText != query.spokenTitle) return searchDeezer(query.spokenTitle)
        return null
    }

    private fun searchDeezer(query: String): Track? {
        val url = "https://api.deezer.com/search?limit=5&q=${URLEncoder.encode(query, "UTF-8")}"
        return parseTracks(Http.get(url, timeoutMs = 8000), "Deezer 试听", 1, fullLength = false).firstOrNull()
    }

    private fun searchItunes(query: String): Track? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val cn = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=5&country=CN"
        parseTracks(Http.get(cn, timeoutMs = 8000), "Apple 试听", 2, fullLength = false).firstOrNull()?.let { return it }
        val us = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=5&country=US"
        return parseTracks(Http.get(us, timeoutMs = 8000), "Apple 试听", 2, fullLength = false).firstOrNull()
    }

    private fun searchArchive(query: MusicQuery): Track? {
        val q = URLEncoder.encode("mediatype:audio AND ${query.searchText}", "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$q&fl[]=identifier&fl[]=title&fl[]=creator&rows=5&output=json"
        val body = Http.get(url, timeoutMs = 8000)
        val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs") ?: return null
        val limit = minOf(docs.length(), 3)
        for (index in 0 until limit) {
            val doc = docs.optJSONObject(index) ?: continue
            loadArchiveTrack(doc, query)?.let { return it }
        }
        return null
    }

    private fun loadArchiveTrack(doc: JSONObject, query: MusicQuery): Track? {
        val identifier = flexibleString(doc, "identifier").ifBlank { return null }
        val meta = Http.get("https://archive.org/metadata/${URLEncoder.encode(identifier, "UTF-8")}", timeoutMs = 8000)
        val files = JSONObject(meta).optJSONArray("files") ?: return null
        val mp3s = buildList {
            for (index in 0 until files.length()) {
                val file = files.optJSONObject(index) ?: continue
                val name = file.optString("name")
                if (!name.endsWith(".mp3", true)) continue
                val size = when (val raw = file.opt("size")) {
                    is Number -> raw.toLong()
                    is String -> raw.toLongOrNull() ?: -1L
                    else -> -1L
                }
                if (size in 200_000..20_000_000) add(name to size)
            }
        }
        val best = mp3s.firstOrNull { it.first.contains("64kb", true) } ?: mp3s.minByOrNull { it.second }
        val fileName = best?.first ?: return null
        val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        val title = flexibleString(doc, "title").ifBlank { query.spokenTitle }.take(40)
        val artist = flexibleString(doc, "creator").take(40)
        return Track(
            title = title,
            artist = artist,
            url = "https://archive.org/download/$identifier/$encodedName",
            source = "自由音频",
            fullLength = true,
            priority = 3,
        )
    }

    private fun parseTracks(body: String, source: String, priority: Int, fullLength: Boolean): List<Track> {
        val root = try {
            JSONTokener(body).nextValue()
        } catch (_: Exception) {
            return emptyList()
        }
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("tracks")
                ?: root.optJSONArray("data")
                ?: root.optJSONArray("results")
                ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = jsonUrl(item)
                if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                val title = item.optString("title").ifBlank { item.optString("name") }.ifBlank { item.optString("trackName") }
                if (title.isBlank()) continue
                add(
                    Track(
                        title = title,
                        artist = jsonArtist(item),
                        url = url,
                        source = source,
                        fullLength = fullLength,
                        priority = priority,
                    ),
                )
            }
        }
    }

    private fun jsonArtist(item: JSONObject): String {
        return when (val artist = item.opt("artist")) {
            is String -> artist
            is JSONObject -> artist.optString("name")
            else -> item.optString("artistName").ifBlank { item.optString("singer") }
        }
    }

    private fun jsonUrl(item: JSONObject): String {
        return listOf("url", "preview", "play_url", "previewUrl", "stream", "audio")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http") } }
            .orEmpty()
    }

    private fun flexibleString(obj: JSONObject, key: String): String {
        return when (val value = obj.opt(key)) {
            is String -> value
            is JSONArray -> if (value.length() > 0) value.optString(0) else ""
            else -> ""
        }
    }

    private const val TAG = "XiaoAi"
}
