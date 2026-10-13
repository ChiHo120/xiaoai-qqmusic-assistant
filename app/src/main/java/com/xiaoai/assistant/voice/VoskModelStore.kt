package com.xiaoai.assistant.voice

import android.content.Context
import android.os.StatFs
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object VoskModelStore {
    private const val DIR = "vosk-model-small-cn-0.22"
    private val urls = listOf(
        "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
    )

    fun locate(context: Context): File? {
        val root = File(context.filesDir, DIR)
        if (isReady(root)) return root
        root.listFiles()?.forEach { child ->
            if (child.isDirectory && isReady(child)) return child
        }
        return null
    }

    fun download(context: Context, onProgress: (Float) -> Unit) {
        val stat = StatFs(context.filesDir.absolutePath)
        if (stat.availableBytes < 150L * 1024 * 1024) {
            throw java.io.IOException("手机空间不足，至少留出 150MB")
        }
        var last: Exception? = null
        for (url in urls) {
            val zip = File(context.cacheDir, "vosk-cn.zip")
            try {
                File(context.filesDir, DIR).deleteRecursively()
                zip.delete()
                downloadUrl(url, zip, onProgress)
                unzip(zip, context.filesDir)
                zip.delete()
                if (locate(context) != null) return
                last = java.io.IOException("语音包解压后不完整")
            } catch (error: Exception) {
                last = error
                zip.delete()
            }
        }
        throw last ?: java.io.IOException("语音包下载失败")
    }

    private fun isReady(dir: File): Boolean {
        return File(dir, "am").isDirectory && File(dir, "conf").isDirectory
    }

    private fun downloadUrl(url: String, dest: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "XiaoAiAssistant/1.0")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw java.io.IOException("语音包下载失败 HTTP $code")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var readTotal = 0L
                    var lastReport = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        readTotal += count
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 200) {
                            lastReport = now
                            val progress = if (total > 0) (readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f
                            onProgress(progress)
                        }
                    }
                }
            }
            if (dest.length() < 1024 * 1024) throw java.io.IOException("语音包文件不完整")
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    private fun unzip(zip: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = safeDest(destDir, entry.name) ?: continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
    }

    private fun safeDest(root: File, name: String): File? {
        val rootPath = root.canonicalFile.path
        val out = File(root, name).canonicalFile
        val ok = out.path == rootPath || out.path.startsWith(rootPath + File.separator)
        return if (ok) out else null
    }
}
