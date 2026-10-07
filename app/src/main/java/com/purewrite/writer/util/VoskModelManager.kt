package com.purewrite.writer.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
 *
 * 下载策略：多源镜像 + 本地文件导入兜底
 * 由于官方源 (alphacephei.com) 在国内访问受限，优先尝试国内可访问的镜像源。
 */
object VoskModelManager {

    private const val MODEL_VERSION = "vosk-model-small-cn-0.22"

    /**
     * 模型下载源（按优先级排序，依次尝试）
     * 1. hf-mirror.com —— HuggingFace 国内镜像，大陆可直连
     * 2. HuggingFace 官方
     * 3. Vosk 官方源（海外，国内可能慢/不可达）
     */
    private val MODEL_URLS = listOf(
        "https://hf-mirror.com/vosk-model/vosk-model-small-cn-0.22/resolve/main/vosk-model-small-cn-0.22.zip",
        "https://huggingface.co/vosk-model/vosk-model-small-cn-0.22/resolve/main/vosk-model-small-cn-0.22.zip",
        "https://alphacephei.com/vosk/models/$MODEL_VERSION.zip"
    )

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

    /**
     * 下载并解压模型（多源重试），返回下载进度百分比（0-100）
     * 依次尝试各镜像源，任一成功即返回。
     */
    suspend fun downloadAndExtract(
        context: Context,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val targetDir = getModelDir(context)
        val zipFile = File(context.cacheDir, "model.zip")

        for ((index, url) in MODEL_URLS.withIndex()) {
            try {
                onProgress(1)
                val success = downloadFromUrl(url, zipFile, onProgress)
                if (success && zipFile.exists() && zipFile.length() > 1_000_000) {
                    val extracted = extractZip(zipFile, targetDir.parentFile!!)
                    if (extracted && isModelReady(context)) {
                        zipFile.delete()
                        onProgress(100)
                        return@withContext true
                    }
                }
                // 当前源失败，清理后尝试下一个
                zipFile.delete()
                targetDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
                zipFile.delete()
                targetDir.deleteRecursively()
            }
            // 如果不是最后一个源，提示切换
            if (index < MODEL_URLS.size - 1) {
                onProgress(0) // 重置进度，准备切换源
            }
        }
        false
    }

    /**
     * 从指定 URL 下载模型 zip 到本地文件
     */
    private fun downloadFromUrl(
        url: String,
        target: File,
        onProgress: (Int) -> Unit
    ): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val body = response.body ?: return false
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: 42_000_000L

            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从本地 zip 文件导入模型（用户手动下载后选择）
     */
    suspend fun importFromLocalZip(
        context: Context,
        zipUri: Uri,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(50)
            val targetDir = getModelDir(context)
            val inputStream: InputStream? = context.contentResolver.openInputStream(zipUri)
            if (inputStream == null) {
                onProgress(0)
                return@withContext false
            }

            val extracted = extractZipFromStream(inputStream, targetDir.parentFile!!)
            val ok = extracted && isModelReady(context)
            onProgress(if (ok) 100 else 0)
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress(0)
            false
        }
    }

    /**
     * 解压 zip 文件到目标目录
     */
    private fun extractZip(zipFile: File, destDir: File): Boolean {
        return try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                extractZipStream(zis, destDir)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun extractZipFromStream(inputStream: InputStream, destDir: File): Boolean {
        return try {
            ZipInputStream(inputStream).use { zis ->
                extractZipStream(zis, destDir)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun extractZipStream(zis: ZipInputStream, destDir: File) {
        var entry = zis.nextEntry
        val buffer = ByteArray(8192)
        while (entry != null) {
            val outFile = File(destDir, entry.name)
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
