package com.purewrite.writer.util

/**
 * 中文字数统计工具
 * 符合中文创作逻辑：
 * - 中文字符（包括中文标点）每个算 1 字
 * - 英文单词按空格分词计算
 * - 数字按连续数字串计算
 */
object WordCounter {

    /**
     * 计算中文字符数（含中文标点）
     */
    fun countChineseChars(text: String): Int {
        if (text.isEmpty()) return 0
        var count = 0
        for (ch in text) {
            if (isChineseChar(ch) || isChinesePunctuation(ch)) {
                count++
            }
        }
        return count
    }

    /**
     * 计算总字数（中文按字符，英文按单词，数字按串）
     * 这是中文写作软件最常用的统计方式
     */
    fun countWords(text: String): Int {
        if (text.isEmpty()) return 0
        var count = 0
        var i = 0
        val len = text.length
        while (i < len) {
            val ch = text[i]
            when {
                isChineseChar(ch) || isChinesePunctuation(ch) -> {
                    count++
                    i++
                }
                ch.isLetter() -> {
                    // 英文单词
                    count++
                    while (i < len && (text[i].isLetter() || text[i] == '\'' || text[i] == '-')) {
                        i++
                    }
                }
                ch.isDigit() -> {
                    // 数字串算一个词
                    count++
                    while (i < len && (text[i].isDigit() || text[i] == '.' || text[i] == ',')) {
                        i++
                    }
                }
                else -> i++
            }
        }
        return count
    }

    /**
     * 计算总字符数（含所有字符，不含空白）
     */
    fun countChars(text: String): Int {
        return text.count { !it.isWhitespace() }
    }

    /**
     * 计算段落数
     */
    fun countParagraphs(text: String): Int {
        if (text.isBlank()) return 0
        return text.split("\n").count { it.isNotBlank() }
    }

    /**
     * 预计阅读时间（分钟），中文阅读速度约 400 字/分钟
     */
    fun estimateReadingTime(text: String): Int {
        val words = countWords(text)
        if (words == 0) return 0
        return (words / 400.0).toInt().coerceAtLeast(1)
    }

    private fun isChineseChar(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x4E00..0x9FFF) ||     // CJK 统一表意文字
                (code in 0x3400..0x4DBF) ||     // CJK 扩展 A
                (code in 0x20000..0x2A6DF) ||   // CJK 扩展 B
                (code in 0xF900..0xFAFF) ||     // CJK 兼容
                (code in 0x3000..0x303F)        // CJK 符号和标点（部分）
    }

    private fun isChinesePunctuation(ch: Char): Boolean {
        val code = ch.code
        return (code in 0xFF00..0xFFEF) ||      // 全角字符
                (code in 0x3000..0x303F) ||      // CJK 符号
                ch == '，' || ch == '。' || ch == '！' || ch == '？' ||
                ch == '；' || ch == '：' || ch == '「' || ch == '」' ||
                ch == '『' || ch == '』' || ch == '（' || ch == '）' ||
                ch == '、' || ch == '…' || ch == '—' || ch == '《' || ch == '》'
    }
}
