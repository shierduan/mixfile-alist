package com.purewrite.writer.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.purewrite.writer.data.db.ChapterEntity

/**
 * 小说排版格式化器
 * 将纯文本章节内容渲染为网络小说/书籍的排版样式：
 * - 章节标题居中、加粗、大字号
 * - 段落首行缩进两个中文字符
 * - 段落之间留白
 * - 正文两端对齐
 */
object NovelFormatter {

    /**
     * 将章节内容格式化为小说排版的 Spannable
     * @param chapter 章节数据
     * @param fontSize 正文字号（sp）
     * @param titleSize 标题字号（sp）
     * @param textColor 正文颜色
     * @param titleColor 标题颜色
     * @return 格式化后的 Spannable
     */
    fun formatChapter(
        chapter: ChapterEntity,
        fontSize: Int,
        titleSize: Int,
        textColor: Int,
        titleColor: Int
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        // 1. 章节标题（居中、加粗、大字号）
        val title = chapter.title.ifBlank { "未命名章节" }
        sb.append("  \n")  // 顶部留白
        val titleStart = sb.length
        sb.append(title)
        sb.setSpan(
            AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
            titleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.setSpan(StyleSpan(Typeface.BOLD), titleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(AbsoluteSizeSpan(titleSize, true), titleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(titleColor), titleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n\n")

        // 2. 装饰分隔线
        val dividerStart = sb.length
        sb.append("── · ──\n\n")
        sb.setSpan(
            AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
            dividerStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.setSpan(RelativeSizeSpan(0.8f), dividerStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // 3. 正文段落
        val content = chapter.content
        val paragraphs = content.split("\n").filter { it.isNotBlank() }

        paragraphs.forEachIndexed { index, para ->
            val trimmed = para.trim()
            // 段落首行缩进两个全角空格
            val paraStart = sb.length
            sb.append("\u3000\u3000")  // 全角空格缩进
            sb.append(trimmed)
            sb.setSpan(
                AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_NORMAL),
                paraStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(AbsoluteSizeSpan(fontSize, true), paraStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(textColor), paraStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n\n")
        }

        // 4. 章末标记
        if (paragraphs.isNotEmpty()) {
            val endStart = sb.length
            sb.append("── 本章完 ──\n")
            sb.setSpan(
                AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                endStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(RelativeSizeSpan(0.85f), endStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return sb
    }

    /**
     * 获取预览用的纯文本（带缩进和分隔的排版文本）
     */
    fun formatPlainText(chapter: ChapterEntity): String {
        val sb = StringBuilder()
        sb.append("\n")
        sb.append(centerText(chapter.title.ifBlank { "未命名章节" }))
        sb.append("\n\n")
        sb.append(centerText("── · ──"))
        sb.append("\n\n")

        chapter.content.split("\n").filter { it.isNotBlank() }.forEach { para ->
            sb.append("\u3000\u3000")  // 全角空格缩进
            sb.append(para.trim())
            sb.append("\n\n")
        }
        sb.append(centerText("── 本章完 ──"))
        sb.append("\n")
        return sb.toString()
    }

    private fun centerText(text: String): String {
        return text  // TextView 用 AlignmentSpan 处理居中
    }
}
