package com.xiaoai.assistant.net

import com.xiaoai.assistant.logic.MusicQuery
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/** REST adapter for l-1124/QQMusicApi's bundled Web service. */
object QqMusicClient {
    fun normalizeBase(input: String): String {
        val value = input.trim().trimEnd('/')
        if (value.isBlank()) return ""
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null) {
            "请填写 QQMusicApi 服务地址，例如 http://192.168.1.10:8080"
        }
        return value
    }

    fun search(
        baseUrl: String,
        query: MusicQuery,
        get: (String) -> String = { Http.get(it, timeoutMs = 8000) },
        post: (String, String) -> String = { url, body -> Http.postJson(url, body, timeoutMs = 8000) },
    ): List<Track> {
        val base = normalizeBase(baseUrl)
        if (base.isBlank()) return emptyList()
        val keyword = URLEncoder.encode(query.searchText, "UTF-8")
        val songs = data(get("$base/search/search_by_type?keyword=$keyword&search_type=0&num=5&highlight=false"))
            .optJSONArray("song") ?: return emptyList()
        val candidates = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }
            .filter { it.optString("mid").isNotBlank() }
            .filter { song ->
                val artist = query.searchText.takeIf { it.startsWith(query.spokenTitle + " ") }
                    ?.removePrefix(query.spokenTitle + " ")?.trim().orEmpty()
                val singers = song.optJSONArray("singer") ?: JSONArray()
                artist.isBlank() || (0 until singers.length()).any {
                    singers.optJSONObject(it)?.optString("name")?.contains(artist, ignoreCase = true) == true
                }
            }
            .sortedByDescending { it.optString("title").ifBlank { it.optString("name") } == query.spokenTitle }
            .take(3)
        if (candidates.isEmpty()) return emptyList()
        val cdns = data(get("$base/song/get_cdn_dispatch")).optJSONArray("sip")
            ?: throw IOException("QQ 音乐没有返回音频节点")
        val cdn = (0 until cdns.length()).map { cdns.optString(it) }
            .firstOrNull { it.startsWith("https://") }
            ?: (0 until cdns.length()).map { cdns.optString(it) }.firstOrNull { it.startsWith("http://") }
            ?: throw IOException("QQ 音乐音频节点不可用")
        val files = JSONArray()
        candidates.forEach { song ->
            files.put(JSONObject().put("mid", song.getString("mid")).apply {
                put("song_type", song.optInt("type", 0))
                song.optJSONObject("file")?.optString("media_mid")?.takeIf { it.isNotBlank() }
                    ?.let { put("media_mid", it) }
            })
        }
        // Omitting file_type uses the upstream MP3_128 default (no unstable enum index).
        val urlsData = data(post("$base/song/get_song_urls", JSONObject().put("file_info", files).toString()))
        val urls = urlsData.optJSONArray("data") ?: urlsData.optJSONArray("midurlinfo") ?: return emptyList()
        return candidates.mapNotNull { song ->
            val mid = song.getString("mid")
            val result = (0 until urls.length()).mapNotNull { urls.optJSONObject(it) }
                .firstOrNull { it.optString("mid").ifBlank { it.optString("songmid") } == mid }
                ?: return@mapNotNull null
            if (result.optInt("result", -1) != 0) return@mapNotNull null
            val path = result.optString("purl")
            if (path.isBlank()) return@mapNotNull null
            val url = URI(cdn).resolve(path).toString()
            if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
            val singers = song.optJSONArray("singer") ?: JSONArray()
            Track(
                title = song.optString("title").ifBlank { song.optString("name") }.ifBlank { query.spokenTitle },
                artist = (0 until singers.length()).mapNotNull { singers.optJSONObject(it)?.optString("name") }
                    .filter { it.isNotBlank() }.joinToString("、"),
                url = url,
                source = "QQ 音乐",
                fullLength = true,
                priority = -1,
            )
        }
    }

    private fun data(body: String): JSONObject {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) throw IOException("QQMusicApi：${root.optString("msg", "请求失败")}")
        return root.optJSONObject("data") ?: throw IOException("QQMusicApi 返回格式不正确")
    }
}
