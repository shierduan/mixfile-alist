package com.purewrite.writer.util

import android.content.Context
import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.data.db.VolumeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份与恢复管理器
 *
 * 备份格式：JSON，包含全部书籍、卷、章节，以及元信息。
 * 通过 SAF（Storage Access Framework）写入用户选定的位置，
 * 支持本地存储及用户可信云存储空间（如 Google Drive、OneDrive 等）。
 */
object BackupManager {

    private const val BACKUP_VERSION = 1

    /** 备份数据结构 */
    data class BackupData(
        val version: Int = BACKUP_VERSION,
        val createdAt: Long = System.currentTimeMillis(),
        val appVersion: String = "1.4.0",
        val books: List<BookEntity>,
        val volumes: List<VolumeEntity>,
        val chapters: List<ChapterEntity>
    )

    /**
     * 序列化备份数据为 JSON 字符串
     */
    fun serialize(data: BackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("createdAt", data.createdAt)
        root.put("appVersion", data.appVersion)
        root.put("createdAtStr", formatDate(data.createdAt))

        val booksArray = JSONArray()
        for (book in data.books) booksArray.put(bookToJson(book))
        root.put("books", booksArray)

        val volumesArray = JSONArray()
        for (v in data.volumes) volumesArray.put(volumeToJson(v))
        root.put("volumes", volumesArray)

        val chaptersArray = JSONArray()
        for (c in data.chapters) chaptersArray.put(chapterToJson(c))
        root.put("chapters", chaptersArray)

        return root.toString(2)
    }

    /**
     * 从 JSON 字符串反序列化备份数据
     */
    fun deserialize(json: String): BackupData? {
        return try {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            val createdAt = root.optLong("createdAt", 0L)
            val appVersion = root.optString("appVersion", "")

            val books = mutableListOf<BookEntity>()
            val booksArr = root.optJSONArray("books") ?: JSONArray()
            for (i in 0 until booksArr.length()) {
                jsonToBook(booksArr.getJSONObject(i))?.let { books.add(it) }
            }

            val volumes = mutableListOf<VolumeEntity>()
            val volumesArr = root.optJSONArray("volumes") ?: JSONArray()
            for (i in 0 until volumesArr.length()) {
                jsonToVolume(volumesArr.getJSONObject(i))?.let { volumes.add(it) }
            }

            val chapters = mutableListOf<ChapterEntity>()
            val chaptersArr = root.optJSONArray("chapters") ?: JSONArray()
            for (i in 0 until chaptersArr.length()) {
                jsonToChapter(chaptersArr.getJSONObject(i))?.let { chapters.add(it) }
            }

            BackupData(version, createdAt, appVersion, books, volumes, chapters)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将备份数据写入输出流（用户通过 SAF 选定的位置）
     */
    fun writeBackup(data: BackupData, outputStream: OutputStream) {
        outputStream.use {
            it.write(serialize(data).toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * 从输入流读取备份数据（用户通过 SAF 选定的文件）
     */
    fun readBackup(inputStream: InputStream): BackupData? {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return deserialize(json)
    }

    /** 生成备份文件名 */
    fun generateBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        return "purewrite_backup_$dateStr.json"
    }

    /** 格式化时间戳 */
    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(ts))
    }

    // ============ BookEntity ============
    private fun bookToJson(book: BookEntity): JSONObject {
        val obj = JSONObject()
        obj.put("id", book.id)
        obj.put("title", book.title)
        obj.put("author", book.author)
        obj.put("description", book.description)
        obj.put("coverColor", book.coverColor)
        obj.put("createdAt", book.createdAt)
        obj.put("updatedAt", book.updatedAt)
        obj.put("lastOpenedAt", book.lastOpenedAt)
        return obj
    }

    private fun jsonToBook(obj: JSONObject): BookEntity? {
        return try {
            BookEntity(
                id = obj.optLong("id", 0L),
                title = obj.optString("title", ""),
                author = obj.optString("author", ""),
                description = obj.optString("description", ""),
                coverColor = obj.optInt("coverColor", 0xFF1A73E8.toInt()),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                lastOpenedAt = obj.optLong("lastOpenedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    // ============ VolumeEntity ============
    private fun volumeToJson(v: VolumeEntity): JSONObject {
        val obj = JSONObject()
        obj.put("id", v.id)
        obj.put("bookId", v.bookId)
        obj.put("title", v.title)
        obj.put("order", v.order)
        obj.put("createdAt", v.createdAt)
        obj.put("updatedAt", v.updatedAt)
        return obj
    }

    private fun jsonToVolume(obj: JSONObject): VolumeEntity? {
        return try {
            VolumeEntity(
                id = obj.optLong("id", 0L),
                bookId = obj.optLong("bookId", 0L),
                title = obj.optString("title", ""),
                order = obj.optInt("order", 0),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    // ============ ChapterEntity ============
    private fun chapterToJson(c: ChapterEntity): JSONObject {
        val obj = JSONObject()
        obj.put("id", c.id)
        obj.put("volumeId", c.volumeId)
        obj.put("title", c.title)
        obj.put("content", c.content)
        obj.put("order", c.order)
        obj.put("wordCount", c.wordCount)
        obj.put("createdAt", c.createdAt)
        obj.put("updatedAt", c.updatedAt)
        return obj
    }

    private fun jsonToChapter(obj: JSONObject): ChapterEntity? {
        return try {
            ChapterEntity(
                id = obj.optLong("id", 0L),
                volumeId = obj.optLong("volumeId", 0L),
                title = obj.optString("title", ""),
                content = obj.optString("content", ""),
                order = obj.optInt("order", 0),
                wordCount = obj.optInt("wordCount", 0),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }
}
