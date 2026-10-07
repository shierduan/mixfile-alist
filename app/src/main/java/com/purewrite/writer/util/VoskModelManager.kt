package com.purewrite.writer.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Vosk 中文语音模型下载器
 *
 * 模型目录结构（解压后）：
 *   vosk-model-small-cn-0.22/
 *     ├── am/              声学模型
 *     ├── conf/            配置
 *     ├── graph/           图模型
 *     ├── ivector/         说话人向量
 *     └── README
 */
object VoskModelManager {

    private const val MODEL_VERSION = "vosk-model-small-cn-0.22"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_VERSION.zip"

    /** 模型在本地解压后的目录 */
    fun getModelDir(context: Context): File {
        return File(context.filesDir, MODEL_VERSION)
    }

    /** 模型是否已就绪 */
    fun isModelReady(context: Context): Boolean {
        val dir = getModelDir(context)
        // 必须存在 conf 目录和 am 目录
        return dir.isDirectory &&
            File(dir, "conf").isDirectory &&
            File(dir, "am").isDirectory
    }

    /** 下载并解压模型，返回下载进度百分比（0-100） */
    suspend fun downloadAndExtract(
        context: Context,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = getModelDir(context)
            // 临时下载 zip
            val zipFile = File(context.cacheDir, "model.zip")

            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(MODEL_URL).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: 42_000_000L

            // 流式下载
            body.byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloaded = 0L
                    var lastReported = -1
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val percent = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent)
                        }
                    }
                }
            }

            onProgress(99)
            // 解压 zip
            targetDir.mkdirs()
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    val outFile = File(targetDir.parentFile, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // zip 解压后会在 filesDir 下形成 vosk-model-small-cn-0.22/ 目录
            // targetDir 已经指向该目录，校验
            val ok = isModelReady(context)
            if (ok) {
                zipFile.delete()
            }
            onProgress(100)
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 清理已下载的模型（用于设置页管理空间） */
    fun deleteModel(context: Context): Boolean {
        return getModelDir(context).deleteRecursively()
    }

    /** 模型大小（已就绪时） */
    fun getModelSize(context: Context): Long {
        val dir = getModelDir(context)
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
