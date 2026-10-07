package com.purewrite.writer.util

import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.data.db.ChapterEntity

/**
 * 轻量级 Markdown 生成器
 * 将纯写作的数据转换为 Markdown 文本，用于预览和导出
 */
object MarkdownParser {

    /**
     * 将书籍 + 章节列表转为完整 Markdown 文本
     */
    fun bookToMarkdown(book: BookEntity, chapters: List<ChapterEntity>): String {
        val sb = StringBuilder()
        sb.append("# ").append(book.title).append("\n\n")
        if (book.author.isNotBlank()) {
            sb.append("**作者：").append(book.author).append("**\n\n")
        }
        if (book.description.isNotBlank()) {
            sb.append(book.description).append("\n\n")
        }
        sb.append("---\n\n")
        chapters.forEach { ch ->
            sb.append("## ").append(ch.title).append("\n\n")
            sb.append(ch.content).append("\n\n")
        }
        return sb.toString()
    }

    /**
     * 将单章节转为 Markdown 文本
     */
    fun chapterToMarkdown(chapter: ChapterEntity): String {
        val sb = StringBuilder()
        sb.append("## ").append(chapter.title).append("\n\n")
        sb.append(chapter.content)
        return sb.toString()
    }

    /**
     * 将 Markdown 文本转为 HTML（用于 WebView 渲染）
     * 轻量级实现，支持常见语法
     */
    fun markdownToHtml(markdown: String): String {
        val lines = markdown.split("\n")
        val sb = StringBuilder()
        var inList = false
        var inCodeBlock = false
        var codeContent = StringBuilder()

        fun closeList() {
            if (inList) {
                sb.append("</ul>\n")
                inList = false
            }
        }

        for (line in lines) {
            when {
                // 代码块
                line.startsWith("```") -> {
                    if (inCodeBlock) {
                        sb.append("<pre><code>").append(escapeHtml(codeContent.toString().trim()))
                            .append("</code></pre>\n")
                        codeContent.clear()
                        inCodeBlock = false
                    } else {
                        closeList()
                        inCodeBlock = true
                    }
                }
                inCodeBlock -> {
                    codeContent.append(line).append("\n")
                }
                // 标题
                line.startsWith("### ") -> {
                    closeList()
                    sb.append("<h3>").append(formatInline(line.substring(4))).append("</h3>\n")
                }
                line.startsWith("## ") -> {
                    closeList()
                    sb.append("<h2>").append(formatInline(line.substring(3))).append("</h2>\n")
                }
                line.startsWith("# ") -> {
                    closeList()
                    sb.append("<h1>").append(formatInline(line.substring(2))).append("</h1>\n")
                }
                // 分隔线
                line.trim() == "---" || line.trim() == "***" -> {
                    closeList()
                    sb.append("<hr/>\n")
                }
                // 引用
                line.startsWith("> ") -> {
                    closeList()
                    sb.append("<blockquote>").append(formatInline(line.substring(2)))
                        .append("</blockquote>\n")
                }
                // 列表
                line.startsWith("- ") || line.startsWith("* ") -> {
                    if (!inList) {
                        sb.append("<ul>\n")
                        inList = true
                    }
                    sb.append("<li>").append(formatInline(line.substring(2))).append("</li>\n")
                }
                // 空行
                line.isBlank() -> {
                    closeList()
                    sb.append("\n")
                }
                // 普通段落
                else -> {
                    closeList()
                    sb.append("<p>").append(formatInline(line)).append("</p>\n")
                }
            }
        }
        closeList()
        if (inCodeBlock) {
            sb.append("<pre><code>").append(escapeHtml(codeContent.toString().trim()))
                .append("</code></pre>\n")
        }
        return sb.toString()
    }

    /**
     * 处理行内格式：粗体、斜体、行内代码、链接
     */
    private fun formatInline(text: String): String {
        var result = escapeHtml(text)
        // 粗体 **text** 或 __text__
        result = Regex("\\*\\*(.+?)\\*\\*").replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        result = Regex("__(.+?)__").replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        // 斜体 *text* 或 _text_
        result = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").replace(result) { "<em>${it.groupValues[1]}</em>" }
        // 行内代码 `code`
        result = Regex("`(.+?)`").replace(result) { "<code>${it.groupValues[1]}</code>" }
        // 链接 [text](url)
        result = Regex("\\[(.+?)]\\((.+?)\\)").replace(result) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        return result
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
