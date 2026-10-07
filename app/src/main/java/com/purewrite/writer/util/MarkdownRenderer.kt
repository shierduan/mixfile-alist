package com.purewrite.writer.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan

/**
 * 将 Markdown 文本渲染为 Spannable，用于 TextView 显示
 * 轻量级原生渲染，无需 WebView
 */
object MarkdownRenderer {

    fun render(markdown: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val lines = markdown.split("\n")
        var inCodeBlock = false

        for (line in lines) {
            when {
                line.startsWith("```") -> {
                    inCodeBlock = !inCodeBlock
                    sb.append("\n")
                }
                inCodeBlock -> {
                    sb.append(line).append("\n")
                }
                line.startsWith("### ") -> {
                    sb.append("\n")
                    val start = sb.length
                    sb.append(line.substring(4))
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.15f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n\n")
                }
                line.startsWith("## ") -> {
                    sb.append("\n")
                    val start = sb.length
                    sb.append(line.substring(3))
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.3f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n\n")
                }
                line.startsWith("# ") -> {
                    sb.append("\n")
                    val start = sb.length
                    sb.append(line.substring(2))
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.6f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n\n")
                }
                line.trim() == "---" || line.trim() == "***" -> {
                    sb.append("————————————————\n\n")
                }
                line.startsWith("> ") -> {
                    val start = sb.length
                    sb.append(line.substring(2))
                    sb.setSpan(QuoteSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val start = sb.length
                    sb.append(line.substring(2))
                    sb.setSpan(BulletSpan(24), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n")
                }
                line.isBlank() -> {
                    sb.append("\n")
                }
                else -> {
                    appendInlineFormatted(sb, line)
                    sb.append("\n")
                }
            }
        }
        return sb
    }

    /**
     * 处理行内格式：粗体、斜体、行内代码、删除线、链接
     */
    private fun appendInlineFormatted(sb: SpannableStringBuilder, text: String) {
        var i = 0
        while (i < text.length) {
            // 粗体 **text**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    val start = sb.length
                    sb.append(text.substring(i + 2, end))
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 2
                    continue
                }
            }
            // 删除线 ~~text~~
            if (i + 1 < text.length && text[i] == '~' && text[i + 1] == '~') {
                val end = text.indexOf("~~", i + 2)
                if (end > 0) {
                    val start = sb.length
                    sb.append(text.substring(i + 2, end))
                    sb.setSpan(StrikethroughSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 2
                    continue
                }
            }
            // 斜体 *text*
            if (text[i] == '*') {
                val end = text.indexOf('*', i + 1)
                if (end > 0 && text[end - 1] != '*') {
                    val start = sb.length
                    sb.append(text.substring(i + 1, end))
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 1
                    continue
                }
            }
            // 行内代码 `code`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    val start = sb.length
                    sb.append(text.substring(i + 1, end))
                    sb.setSpan(
                        android.text.style.TypefaceSpan("monospace"),
                        start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    i = end + 1
                    continue
                }
            }
            // 链接 [text](url)
            if (text[i] == '[') {
                val textEnd = text.indexOf(']', i + 1)
                if (textEnd > 0 && textEnd + 1 < text.length && text[textEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', textEnd + 2)
                    if (urlEnd > 0) {
                        val linkText = text.substring(i + 1, textEnd)
                        val url = text.substring(textEnd + 2, urlEnd)
                        val start = sb.length
                        sb.append(linkText)
                        sb.setSpan(URLSpan(url), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = urlEnd + 1
                        continue
                    }
                }
            }
            sb.append(text[i])
            i++
        }
    }
}
