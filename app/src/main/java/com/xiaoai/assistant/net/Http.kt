package com.xiaoai.assistant.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15000): String {
        return request(url, "GET", null, headers, timeoutMs)
    }

    fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 60000,
    ): String {
        return request(url, "POST", json, headers, timeoutMs)
    }

    private fun request(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "XiaoAiAssistant/1.0")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code ${text.take(200)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}
