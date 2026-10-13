package com.xiaoai.assistant.net

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ModelFetch(
    val baseUrl: String,
    val models: List<String>,
)

object AiClient {
    private const val SYSTEM = "你是手机里的语音助手小爱同学。用简短口语中文回答，适合直接朗读，通常不超过60个字。不要使用 Markdown、列表符号或表情符号。你不能查看实时天气、新闻和股票，遇到这类问题就直说自己查不了，不要编造。"

    fun fetchModels(baseInput: String, apiKey: String): ModelFetch {
        val bases = ApiUrls.candidateBases(baseInput)
        if (bases.isEmpty()) throw IOException("请先填写接口地址")
        var last: Exception? = null
        for (base in bases) {
            try {
                val models = requestModels(base, apiKey)
                if (models.isNotEmpty()) return ModelFetch(base, models)
            } catch (error: Exception) {
                last = error
            }
        }
        throw last ?: IOException("没有获取到模型")
    }

    fun chat(baseUrl: String, apiKey: String, model: String, history: List<Pair<String, String>>, userText: String): String {
        val root = ApiUrls.normalizeBase(baseUrl)
        if (root.isBlank()) throw IOException("请先填写接口地址")
        if (model.isBlank()) throw IOException("请先填写或选择模型")
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM))
        history.takeLast(8).forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.7)
            .put("stream", false)
            .put("messages", messages)
        val headers = buildMap {
            if (apiKey.isNotBlank()) put("Authorization", "Bearer $apiKey")
        }
        val body = Http.postJson("$root/chat/completions", payload.toString(), headers, 60000)
        return parseReply(body)
    }

    private fun requestModels(base: String, apiKey: String): List<String> {
        val headers = buildMap {
            if (apiKey.isNotBlank()) put("Authorization", "Bearer $apiKey")
        }
        val body = Http.get("$base/models", headers, 20000)
        return parseModelIds(body)
    }

    fun parseModelIds(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            return readIds(JSONArray(trimmed)).distinct().sorted()
        }
        val obj = JSONObject(trimmed)
        if (obj.has("error")) {
            throw IOException(errorMessage(obj))
        }
        val array = obj.optJSONArray("data") ?: obj.optJSONArray("models") ?: JSONArray()
        return readIds(array).distinct().sorted()
    }

    private fun readIds(array: JSONArray): List<String> = buildList {
        for (index in 0 until array.length()) {
            when (val value = array.opt(index)) {
                is String -> if (value.isNotBlank()) add(value)
                is JSONObject -> {
                    val name = value.optString("id").ifBlank { value.optString("name") }
                    if (name.isNotBlank()) add(name)
                }
            }
        }
    }

    private fun parseReply(body: String): String {
        val obj = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw IOException("接口返回的不是 JSON：${body.take(80)}")
        }
        if (obj.has("error")) throw IOException(errorMessage(obj))
        val choice = obj.optJSONArray("choices")?.optJSONObject(0)
            ?: throw IOException("接口没有返回回答")
        val message = choice.optJSONObject("message")
        val content = message?.opt("content")
        val text = when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    when (val part = content.opt(index)) {
                        is String -> append(part)
                        is JSONObject -> append(part.optString("text"))
                    }
                }
            }
            else -> message?.optString("text").orEmpty()
        }.trim()
        if (text.isBlank()) throw IOException("模型返回了空内容")
        return text
    }

    private fun errorMessage(obj: JSONObject): String {
        val error = obj.opt("error")
        val message = when (error) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> ""
        }
        return message.ifBlank { "接口返回错误" }
    }
}
