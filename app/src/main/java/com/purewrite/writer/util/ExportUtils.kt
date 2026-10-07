package com.purewrite.writer.util

import android.content.Context
import android.net.Uri
import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.data.db.ChapterEntity
import java.io.OutputStream

object ExportUtils {

    /**
     * 导出整本书为 TXT 文件
     * 格式：书名 + 作者 + 各章节标题 + 内容
     */
    fun exportBookToTxt(
        context: Context,
        uri: Uri,
        book: BookEntity,
        chapters: List<ChapterEntity>
    ): Boolean {
        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            outputStream?.use { stream ->
                val writer = stream.bufferedWriter(Charsets.UTF_8)
                // 写入书籍信息
                writer.write(book.title)
                writer.newLine()
                if (book.author.isNotBlank()) {
                    writer.write("作者：${book.author}")
                    writer.newLine()
                }
                if (book.description.isNotBlank()) {
                    writer.write(book.description)
                    writer.newLine()
                }
                writer.newLine()
                writer.write("=".repeat(40))
                writer.newLine()
                writer.newLine()

                // 写入各章节
                chapters.forEachIndexed { index, chapter ->
                    writer.write("第${index + 1}章 ${chapter.title}")
                    writer.newLine()
                    writer.newLine()
                    // 段落缩进处理
                    val paragraphs = chapter.content.split("\n")
                    paragraphs.forEach { para ->
                        if (para.isNotBlank()) {
                            writer.write("    $para")
                            writer.newLine()
                        }
                    }
                    writer.newLine()
                    writer.newLine()
                }

                writer.flush()
                writer.close()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 导出单个章节为 TXT
     */
    fun exportChapterToTxt(
        context: Context,
        uri: Uri,
        chapter: ChapterEntity
    ): Boolean {
        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            outputStream?.use { stream ->
                val writer = stream.bufferedWriter(Charsets.UTF_8)
                writer.write(chapter.title)
                writer.newLine()
                writer.newLine()
                val paragraphs = chapter.content.split("\n")
                paragraphs.forEach { para ->
                    if (para.isNotBlank()) {
                        writer.write("    $para")
                        writer.newLine()
                    }
                }
                writer.flush()
                writer.close()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 导出整本书为 Markdown 文件
     * 格式：# 书名 / 作者 / 描述 + ## 各章节标题 + 正文
     */
    fun exportBookToMarkdown(
        context: Context,
        uri: Uri,
        book: BookEntity,
        chapters: List<ChapterEntity>
    ): Boolean {
        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            outputStream?.use { stream ->
                val writer = stream.bufferedWriter(Charsets.UTF_8)
                // 书籍信息
                writer.write("# ${book.title}")
                writer.newLine()
                writer.newLine()
                if (book.author.isNotBlank()) {
                    writer.write("**作者：${book.author}**")
                    writer.newLine()
                    writer.newLine()
                }
                if (book.description.isNotBlank()) {
                    writer.write(book.description)
                    writer.newLine()
                    writer.newLine()
                }
                writer.write("---")
                writer.newLine()
                writer.newLine()

                chapters.forEach { chapter ->
                    writer.write("## ${chapter.title}")
                    writer.newLine()
                    writer.newLine()
                    // 章节内容按段落输出
                    val paragraphs = chapter.content.split("\n")
                    paragraphs.forEach { para ->
                        if (para.isNotBlank()) {
                            writer.write(para)
                            writer.newLine()
                            writer.newLine()
                        }
                    }
                    writer.newLine()
                }

                writer.flush()
                writer.close()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 导出单个章节为 Markdown
     */
    fun exportChapterToMarkdown(
        context: Context,
        uri: Uri,
        chapter: ChapterEntity
    ): Boolean {
        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            outputStream?.use { stream ->
                val writer = stream.bufferedWriter(Charsets.UTF_8)
                writer.write("## ${chapter.title}")
                writer.newLine()
                writer.newLine()
                val paragraphs = chapter.content.split("\n")
                paragraphs.forEach { para ->
                    if (para.isNotBlank()) {
                        writer.write(para)
                        writer.newLine()
                        writer.newLine()
                    }
                }
                writer.flush()
                writer.close()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
