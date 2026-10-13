package com.xiaoai.assistant.net

import com.xiaoai.assistant.logic.MusicQuery
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QqMusicClientTest {
    private val search = """{"code":0,"data":{"song":[
        {"mid":"denied","name":"晴天 (Live)","type":0,"singer":[{"name":"周杰伦"}]},
        {"mid":"original","title":"晴天","type":0,"file":{"media_mid":"media"},"singer":[{"name":"周杰伦"}]}
    ]}}"""

    @Test fun resolvesAuthorizedOriginalAndPreservesMediaMid() {
        val tracks = QqMusicClient.search("http://localhost:8080/", MusicQuery("晴天", "晴天 周杰伦"),
            get = { url ->
                if (url.contains("search_by_type")) {
                    assertTrue(url.contains("search_type=0"))
                    assertTrue(url.contains("%E6%99%B4%E5%A4%A9"))
                    search
                } else """{"code":0,"data":{"sip":["https://cdn.example/"]}}"""
            },
            post = { url, body ->
                assertTrue(url.endsWith("/song/get_song_urls"))
                val request = JSONObject(body)
                assertFalse(request.has("file_type"))
                assertEquals("media", request.getJSONArray("file_info").getJSONObject(0).getString("media_mid"))
                """{"code":0,"data":{"data":[
                    {"mid":"denied","result":104003,"purl":"denied.mp3"},
                    {"mid":"original","result":0,"purl":"M500media.mp3?vkey=abc"}
                ]}}"""
            })
        assertEquals(1, tracks.size)
        assertEquals("晴天", tracks[0].title)
        assertEquals("周杰伦", tracks[0].artist)
        assertEquals("https://cdn.example/M500media.mp3?vkey=abc", tracks[0].url)
    }

    @Test fun missingAuthorizationDoesNotBecomePlayable() {
        val tracks = QqMusicClient.search("https://music.example", MusicQuery("晴天", "晴天"),
            get = { if (it.contains("search_by_type")) search else """{"code":0,"data":{"sip":["https://cdn.example/"]}}""" },
            post = { _, _ -> """{"code":0,"data":{"data":[{"mid":"original","result":0,"purl":""}]}}""" })
        assertTrue(tracks.isEmpty())
    }

    @Test(expected = java.io.IOException::class) fun serviceErrorsAreVisible() {
        QqMusicClient.search("https://music.example", MusicQuery("晴天", "晴天"),
            get = { """{"code":-1,"msg":"expired"}""" })
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsSearchUrlAsServiceBase() {
        QqMusicClient.normalizeBase("https://music.example/search?q={q}")
    }
}
