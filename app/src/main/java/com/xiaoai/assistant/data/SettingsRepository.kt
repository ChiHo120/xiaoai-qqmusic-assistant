package com.xiaoai.assistant.data

import android.content.Context
import com.xiaoai.assistant.net.ApiUrls
import com.xiaoai.assistant.net.QqMusicClient
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var preferQqMusic: Boolean
        get() = prefs.getBoolean(KEY_QQ, true)
        set(value) {
            prefs.edit().putBoolean(KEY_QQ, value).apply()
        }

    var alwaysListen: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ALWAYS, value).apply()
        }

    fun load(): Settings {
        val baseUrl = prefs.getString(KEY_BASE, "").orEmpty()
        val cached = modelsFor(baseUrl)
        return Settings(
            baseUrl = baseUrl,
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, "").orEmpty(),
            musicApi = prefs.getString(KEY_MUSIC_API, "").orEmpty(),
            qqMusicApi = prefs.getString(KEY_QQ_API, "").orEmpty(),
            preferQqMusic = preferQqMusic,
            alwaysListen = alwaysListen,
            models = cached.first,
            modelsCachedAt = cached.second,
        )
    }

    fun save(baseUrl: String, apiKey: String, model: String, musicApi: String) {
        prefs.edit()
            .putString(KEY_BASE, ApiUrls.normalizeBase(baseUrl))
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .putString(KEY_MUSIC_API, musicApi.trim())
            .commit()
    }

    fun saveModels(baseUrl: String, models: List<String>) {
        val key = ApiUrls.normalizeBase(baseUrl)
        val all = runCatching { JSONObject(prefs.getString(KEY_MODEL_CACHE, "{}")) }.getOrElse { JSONObject() }
        val item = JSONObject()
            .put("at", System.currentTimeMillis())
            .put("models", JSONArray(models))
        all.put(key, item)
        prefs.edit().putString(KEY_MODEL_CACHE, all.toString()).commit()
    }

    fun saveQqMusicApi(value: String) {
        prefs.edit().putString(KEY_QQ_API, QqMusicClient.normalizeBase(value)).commit()
    }

    fun clearModels() {
        prefs.edit().remove(KEY_MODEL_CACHE).commit()
    }

    fun modelsFor(baseUrl: String): Pair<List<String>, Long> {
        val key = ApiUrls.normalizeBase(baseUrl)
        val all = runCatching { JSONObject(prefs.getString(KEY_MODEL_CACHE, "{}")) }.getOrNull() ?: return emptyList<String>() to 0L
        val item = all.optJSONObject(key) ?: return emptyList<String>() to 0L
        val array = item.optJSONArray("models") ?: return emptyList<String>() to item.optLong("at")
        val models = buildList {
            for (index in 0 until array.length()) {
                val name = array.optString(index)
                if (name.isNotBlank()) add(name)
            }
        }
        return models to item.optLong("at")
    }

    fun saveHistory(items: List<ChatLine>) {
        val array = JSONArray()
        items.takeLast(40).forEach { line ->
            array.put(JSONObject().put("role", line.role).put("text", line.text))
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun loadHistory(): List<ChatLine> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text")
                if (text.isBlank()) continue
                add(ChatLine(role = item.optString("role", "assistant"), text = text))
            }
        }
    }

    companion object {
        private const val PREFS = "xiaoai_settings"
        private const val KEY_BASE = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_MUSIC_API = "music_api"
        private const val KEY_QQ_API = "qq_music_api"
        private const val KEY_QQ = "prefer_qq_music"
        private const val KEY_ALWAYS = "always_listen"
        private const val KEY_MODEL_CACHE = "model_cache"
        private const val KEY_HISTORY = "history"
    }
}
