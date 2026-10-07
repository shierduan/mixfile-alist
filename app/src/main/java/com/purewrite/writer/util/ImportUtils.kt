package com.purewrite.writer.util

import android.content.Context
import android.net.Uri
import com.purewrite.writer.data.db.ChapterEntity

/**
 * 导入工具：将 TXT / Markdown 文件解析为章节
 *
 * TXT 格式：按空行分段，可按"第X章"行切分多章节
 * Markdown 格式：按 ## 标题切分章节
 */
object ImportUtils {

    /**
     * 导入结果
     */
    data class ImportResult(
        val bookTitle: String,
        val chapters: List<ChapterEntity>
    )

    /**
     * 读取文件内容并解析
     */
    fun importFromFile(context: Context, uri: Uri): ImportResult? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val text = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            inputStream.close()

            // 根据内容判断格式
            val fileName = getFileName(context, uri)
            when {
                fileName.endsWith(".md", ignoreCase = true) ||
                        text.contains(Regex("^#{1,2}\\s", RegexOption.MULTILINE)) -> parseMarkdown(text, fileName)
                else -> parseTxt(text, fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 解析 Markdown 文件
     * 按 ## 或 # 切分章节
     */
    private fun parseMarkdown(text: String, fileName: String): ImportResult {
        val lines = text.lines()
        val chapters = mutableListOf<ChapterEntity>()
        var bookTitle = fileName.removeSuffix(".md").removeSuffix(".MD")
        var currentTitle: String? = null
        val currentContent = StringBuilder()
        var order = 0

        fun flush() {
            if (currentTitle != null || currentContent.isNotBlank()) {
                chapters.add(
                    ChapterEntity(
                        volumeId = -1,
                        title = currentTitle ?: "第${order + 1}章",
                        content = currentContent.toString().trimEnd(),
                        order = order++
                    )
                )
                currentContent.clear()
            }
        }

        for (line in lines) {
            val h1 = Regex("^#\\s+(.+)").matchEntire(line)
            val h2 = Regex("^##\\s+(.+)").matchEntire(line)
            when {
                h1 != null -> {
                    flush()
                    currentTitle = h1.groupValues[1].trim()
                    // 第一个 # 作为书名
                    if (chapters.isEmpty() && bookTitle.isEmpty() || bookTitle == fileName.removeSuffix(".md").removeSuffix(".MD")) {
                        bookTitle = h1.groupValues[1].trim()
                        currentTitle = null
                    }
                }
                h2 != null -> {
                    flush()
                    currentTitle = h2.groupValues[1].trim()
                }
                else -> {
                    if (currentTitle != null || currentContent.isNotBlank() || chapters.isNotEmpty()) {
                        currentContent.append(line).append("\n")
                    }
                }
            }
        }
        flush()

        if (chapters.isEmpty()) {
            chapters.add(
                ChapterEntity(
                    volumeId = -1,
                    title = bookTitle.ifEmpty { "未命名章节" },
                    content = text,
                    order = 0
                )
            )
        }
        return ImportResult(bookTitle.ifBlank { "导入书籍" }, chapters)
    }

    /**
     * 解析 TXT 文件
     * 按"第X章"标题行切分章节
     */
    private fun parseTxt(text: String, fileName: String): ImportResult {
        val bookTitle = fileName.removeSuffix(".txt").removeSuffix(".TXT")
        val chapterPattern = Regex("^\\s*第[0-9零一二三四五六七八九十百千]+[章节回卷]")

        // 查找所有章节标题位置
        val matches = chapterPattern.findAll(text).toList()
        val chapters = mutableListOf<ChapterEntity>()

        if (matches.isEmpty()) {
            // 整个文件作为一个章节
            chapters.add(
                ChapterEntity(
                    volumeId = -1,
                    title = bookTitle.ifBlank { "未命名章节" },
                    content = text.trim(),
                    order = 0
                )
            )
        } else {
            matches.forEachIndexed { index, match ->
                val start = match.range.first
                val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
                val section = text.substring(start, end)
                val firstLineEnd = section.indexOf('\n').let { if (it < 0) section.length else it }
                val title = section.substring(0, firstLineEnd).trim().ifBlank { "第${index + 1}章" }
                val content = if (firstLineEnd < section.length) section.substring(firstLineEnd + 1).trim() else ""
                chapters.add(
                    ChapterEntity(
                        volumeId = -1,
                        title = title,
                        content = content,
                        order = index
                    )
                )
            }
        }

        return ImportResult(bookTitle.ifBlank { "导入书籍" }, chapters)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex("_display_name")
            if (nameIndex >= 0 && it.moveToFirst()) {
                return it.getString(nameIndex) ?: "导入文件"
            }
        }
        return uri.lastPathSegment ?: "导入文件"
    }
}
