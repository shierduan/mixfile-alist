package com.purewrite.writer.util

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 敏感词管理工具
 *
 * 设计原则：
 * - 不内置任何真实敏感词，仅提供中性占位词作为初始模板
 * - 词库由用户自行维护并对内容合规负责
 * - 支持从文件导入 / 导出到文件（JSON 格式）
 * - 检测引擎采用子串匹配，返回命中位置及上下文片段
 */
object SensitiveWordManager {

    private const val FILE_NAME = "sensitive_words.json"
    private const val KEY_VERSION = "version"
    private const val KEY_WORDS = "words"
    private const val KEY_UPDATED_AT = "updatedAt"
    private const val CURRENT_VERSION = 1

    /** 上下文片段前后各截取的字符数 */
    private const val CONTEXT_RADIUS = 15

    /**
     * 词库数据结构
     */
    data class WordLibrary(
        val version: Int = CURRENT_VERSION,
        val words: MutableList<String> = mutableListOf(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * 单个检测命中结果
     */
    data class Match(
        val word: String,
        val start: Int,
        val end: Int,
        val context: String
    )

    // ==================== 词库持久化 ====================

    private fun getLibraryFile(context: Context): File =
        File(context.filesDir, FILE_NAME)

    /**
     * 读取本地词库。若文件不存在则返回内置中性占位模板。
     */
    fun loadLibrary(context: Context): WordLibrary {
        val file = getLibraryFile(context)
        return if (file.exists()) {
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val version = json.optInt(KEY_VERSION, CURRENT_VERSION)
                val updatedAt = json.optLong(KEY_UPDATED_AT, System.currentTimeMillis())
                val wordsArray = json.optJSONArray(KEY_WORDS) ?: JSONArray()
                val words = mutableListOf<String>()
                for (i in 0 until wordsArray.length()) {
                    val w = wordsArray.optString(i, "").trim()
                    if (w.isNotEmpty()) words.add(w)
                }
                WordLibrary(version = version, words = words, updatedAt = updatedAt)
            } catch (e: Exception) {
                e.printStackTrace()
                defaultLibrary()
            }
        } else {
            defaultLibrary().also { saveLibrary(context, it) }
        }
    }

    /**
     * 保存词库到本地文件
     */
    fun saveLibrary(context: Context, library: WordLibrary) {
        try {
            val json = JSONObject().apply {
                put(KEY_VERSION, library.version)
                put(KEY_UPDATED_AT, System.currentTimeMillis())
                put(KEY_WORDS, JSONArray(library.words.distinct()))
            }
            getLibraryFile(context).writeText(json.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 内置中性占位词库模板（仅作示例，不含任何真实敏感词）
     */
    private fun defaultLibrary(): WordLibrary = WordLibrary(
        words = mutableListOf("示例词A", "示例词B", "示例词C")
    )

    // ==================== 词库增删 ====================

    fun getWords(context: Context): List<String> =
        loadLibrary(context).words.distinct()

    fun addWord(context: Context, word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val lib = loadLibrary(context)
        if (!lib.words.contains(trimmed)) {
            lib.words.add(trimmed)
            saveLibrary(context, lib)
        }
    }

    fun removeWord(context: Context, word: String) {
        val lib = loadLibrary(context)
        lib.words.remove(word)
        saveLibrary(context, lib)
    }

    fun clearWords(context: Context) {
        saveLibrary(context, WordLibrary(words = mutableListOf()))
    }

    fun replaceWords(context: Context, words: List<String>) {
        val cleaned = words.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        saveLibrary(context, WordLibrary(words = cleaned.toMutableList()))
    }

    // ==================== 检测引擎 ====================

    /**
     * 对文本进行敏感词检测，返回所有命中项（按出现位置排序）
     */
    fun detect(text: String, words: List<String>): List<Match> {
        if (text.isEmpty() || words.isEmpty()) return emptyList()
        val matches = mutableListOf<Match>()
        for (word in words) {
            if (word.isEmpty()) continue
            var index = text.indexOf(word, 0)
            while (index >= 0) {
                val end = index + word.length
                val context = extractContext(text, index, end)
                matches.add(Match(word = word, start = index, end = end, context = context))
                index = text.indexOf(word, end)
            }
        }
        matches.sortBy { it.start }
        return matches
    }

    /**
     * 提取命中词前后的上下文片段，用省略号标识截断
     */
    private fun extractContext(text: String, start: Int, end: Int): String {
        val prefixStart = (start - CONTEXT_RADIUS).coerceAtLeast(0)
        val suffixEnd = (end + CONTEXT_RADIUS).coerceAtMost(text.length)
        val prefix = if (prefixStart > 0) "…" + text.substring(prefixStart, start) else text.substring(0, start)
        val hit = text.substring(start, end)
        val suffix = if (suffixEnd < text.length) text.substring(end, suffixEnd) + "…" else text.substring(end)
        return prefix + hit + suffix
    }

    // ==================== 导入导出 ====================

    /**
     * 导出当前词库到用户指定的 URI（JSON）
     */
    fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val lib = loadLibrary(context)
            val json = JSONObject().apply {
                put(KEY_VERSION, CURRENT_VERSION)
                put(KEY_UPDATED_AT, System.currentTimeMillis())
                put(KEY_WORDS, JSONArray(lib.words))
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从用户指定的 URI 导入词库（JSON 或纯文本逐行）
     * 返回导入的词条列表，失败返回 null
     */
    fun importFromUri(context: Context, uri: Uri): List<String>? {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null

            val words = parseImportContent(content)
            if (words.isEmpty()) return null
            words
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 解析导入内容：优先按 JSON 解析，失败则按纯文本逐行分词
     */
    private fun parseImportContent(content: String): List<String> {
        val trimmed = content.trim()
        // 尝试 JSON 格式
        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)
                val arr = json.optJSONArray(KEY_WORDS)
                if (arr != null) {
                    val result = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val w = arr.optString(i, "").trim()
                        if (w.isNotEmpty()) result.add(w)
                    }
                    if (result.isNotEmpty()) return result.distinct()
                }
            } catch (_: Exception) {
                // 降级到纯文本解析
            }
        }
        // JSON 数组格式
        if (trimmed.startsWith("[")) {
            try {
                val arr = JSONArray(trimmed)
                val result = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val w = arr.optString(i, "").trim()
                    if (w.isNotEmpty()) result.add(w)
                }
                if (result.isNotEmpty()) return result.distinct()
            } catch (_: Exception) {
                // 降级到纯文本解析
            }
        }
        // 纯文本：按换行 / 逗号 / 分号 分隔
        return trimmed.split(Regex("[\\n,，;；]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
