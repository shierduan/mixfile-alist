package com.purewrite.writer.util

/**
 * 中文标点符号自动转换工具
 * 将英文标点自动转换为中文标点，符合中文排版习惯
 */
object ChinesePunctuationConverter {

    private val punctuationMap: Map<Char, String> = mapOf(
        ',' to "，",
        '.' to "。",
        '?' to "？",
        '!' to "！",
        ';' to "；",
        ':' to "：",
        '(' to "（",
        ')' to "）",
        '[' to "【",
        ']' to "】",
        '{' to "｛",
        '}' to "｝",
        '<' to "《",
        '>' to "》",
        '/' to "、",
        '\\' to "、",
        '|' to "｜",
        '~' to "～",
        '^' to "……",
        '_' to "——",
        '-' to "—"
    )

    /**
     * 转换单个字符为中文标点
     * @return 转换后的字符串，如果不是标点则返回 null
     */
    fun convertChar(ch: Char): String? {
        return punctuationMap[ch]
    }

    /**
     * 转换整个文本中的英文标点为中文标点
     */
    fun convertText(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val converted = punctuationMap[ch]
            if (converted != null) {
                sb.append(converted)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * 判断是否应该自动转换标点
     * 根据前一个字符判断：如果前一个字符是中文，则转换
     */
    fun shouldConvert(prevChar: Char?, currentChar: Char): Boolean {
        if (currentChar !in punctuationMap) return false
        prevChar ?: return false
        return isChineseContext(prevChar)
    }

    private fun isChineseContext(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x4E00..0x9FFF) ||
                (code in 0x3400..0x4DBF) ||
                (code in 0xFF00..0xFFEF) ||
                (code in 0x3000..0x303F) ||
                ch in listOf('，', '。', '！', '？', '；', '：', '）', '】', '」', '』', '》')
    }

    /**
     * 智能引号处理：根据位置转换为中文引号
     */
    fun convertQuote(prevChar: Char?, quote: Char): Char {
        return if (prevChar == null || prevChar.isWhitespace() || isOpeningContext(prevChar)) {
            if (quote == '"') '“' else '‘'
        } else {
            if (quote == '"') '”' else '’'
        }
    }

    private fun isOpeningContext(ch: Char): Boolean {
        return ch in listOf('，', '。', '！', '？', '；', '：', '）', '、')
    }
}
