package com.xiaoai.assistant.net

object ApiUrls {
    fun normalizeBase(input: String): String {
        var value = input.trim().trimEnd('/')
        if (value.isBlank()) return ""
        value = value.removeSuffix("/chat/completions").removeSuffix("/models").trimEnd('/')
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://$value"
        }
        return value
    }

    fun candidateBases(input: String): List<String> {
        val normalized = normalizeBase(input)
        if (normalized.isBlank()) return emptyList()
        val withV1 = if (normalized.endsWith("/v1")) normalized else "$normalized/v1"
        return listOf(normalized, withV1).distinct()
    }
}
