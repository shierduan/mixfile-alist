package com.purewrite.writer.util

import android.content.Context
import com.purewrite.writer.App
import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.data.db.VolumeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动备份管理器
 *
 * 在应用内部存储中维护备份历史，支持自动备份和手动备份。
 * 同时支持导出备份到用户指定位置（本地/云端）。
 */
object AutoBackupManager {

    private const val BACKUP_DIR = "backups"
    private const val MAX_INTERNAL_BACKUPS = 20

    data class BackupRecord(
        val fileName: String,
        val createdAt: Long,
        val sizeBytes: Long,
        val bookCount: Int,
        val chapterCount: Int
    )

    private fun getBackupDir(context: Context): File {
        val dir = File(context.filesDir, BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 创建备份并保存到内部存储
     * @return 备份记录，失败返回 null
     */
    suspend fun createInternalBackup(context: Context): BackupRecord? = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as App
            val repo = app.repository

            val books = repo.getAllBooksList()
            val volumes = repo.getAllVolumesList()
            val chapters = repo.getAllChaptersList()

            val data = BackupManager.BackupData(
                books = books,
                volumes = volumes,
                chapters = chapters
            )

            val json = BackupManager.serialize(data)
            val dir = getBackupDir(context)
            val fileName = BackupManager.generateBackupFileName()
            val file = File(dir, fileName)
            file.writeText(json, Charsets.UTF_8)

            // 清理超过上限的旧备份
            cleanupOldBackups(dir)

            BackupRecord(
                fileName = fileName,
                createdAt = System.currentTimeMillis(),
                sizeBytes = file.length(),
                bookCount = books.size,
                chapterCount = chapters.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 列出所有内部备份
     */
    fun listBackups(context: Context): List<BackupRecord> {
        val dir = getBackupDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val json = file.readText(Charsets.UTF_8)
                val root = JSONObject(json)
                val booksArr = root.optJSONArray("books")
                val chaptersArr = root.optJSONArray("chapters")
                BackupRecord(
                    fileName = file.name,
                    createdAt = file.lastModified(),
                    sizeBytes = file.length(),
                    bookCount = booksArr?.length() ?: 0,
                    chapterCount = chaptersArr?.length() ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    /**
     * 读取指定备份文件的数据
     */
    fun getBackupData(context: Context, fileName: String): BackupManager.BackupData? {
        val file = File(getBackupDir(context), fileName)
        if (!file.exists()) return null
        return BackupManager.deserialize(file.readText(Charsets.UTF_8))
    }

    /**
     * 删除指定备份
     */
    fun deleteBackup(context: Context, fileName: String): Boolean {
        val file = File(getBackupDir(context), fileName)
        return file.delete()
    }

    /**
     * 清理超过上限的旧备份
     */
    private fun cleanupOldBackups(dir: File) {
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: return
        val sorted = files.sortedBy { it.lastModified() }
        val toDelete = sorted.size - MAX_INTERNAL_BACKUPS
        for (i in 0 until toDelete.coerceAtLeast(0)) {
            sorted[i].delete()
        }
    }

    /** 格式化文件大小 */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /** 格式化时间 */
    fun formatTime(ts: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ts))
    }
}
