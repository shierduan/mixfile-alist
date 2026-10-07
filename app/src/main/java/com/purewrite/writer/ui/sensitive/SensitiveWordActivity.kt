package com.purewrite.writer.ui.sensitive

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.purewrite.writer.R
import com.purewrite.writer.databinding.ActivitySensitiveWordBinding
import com.purewrite.writer.databinding.ItemSensitiveResultBinding
import com.purewrite.writer.databinding.ItemWordBinding
import com.purewrite.writer.util.SensitiveWordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 敏感词检测与词库管理页面
 *
 * 入口：编辑器工具栏 / 章节列表（单章或全书）
 * 功能：
 * - 对传入文本进行敏感词检测，展示命中位置及上下文（高亮命中词）
 * - 词库管理：添加、删除、清空、从文件导入、导出到文件
 */
class SensitiveWordActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensitiveWordBinding

    private var detectTitle: String = ""
    private var detectText: String = ""
    private var currentMatches: List<SensitiveWordManager.Match> = emptyList()
    private var currentWords: List<String> = emptyList()

    private val resultAdapter = ResultAdapter()
    private val wordAdapter = WordAdapter(onDelete = { word -> deleteWord(word) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensitiveWordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detectTitle = intent.getStringExtra(EXTRA_TITLE) ?: "检测"
        detectText = intent.getStringExtra(EXTRA_TEXT) ?: ""

        setupToolbar()
        setupRecyclerViews()
        setupButtons()

        loadWords()
        runDetection()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvDetectTitle.text = detectTitle
    }

    private fun setupRecyclerViews() {
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = resultAdapter

        binding.rvWords.layoutManager = LinearLayoutManager(this)
        binding.rvWords.adapter = wordAdapter
    }

    private fun setupButtons() {
        binding.btnRecheck.setOnClickListener { runDetection() }
        binding.btnAddWord.setOnClickListener { showAddWordDialog() }
        binding.btnImportWords.setOnClickListener { pickImportFile() }
        binding.btnExportWords.setOnClickListener { pickExportFile() }
        binding.btnClearWords.setOnClickListener { showClearConfirm() }
    }

    // ==================== 检测 ====================

    private fun runDetection() {
        lifecycleScope.launch {
            val words = withContext(Dispatchers.IO) {
                SensitiveWordManager.getWords(this@SensitiveWordActivity)
            }
            currentWords = words
            binding.tvLibrarySize.text = words.size.toString()

            val matches = withContext(Dispatchers.Default) {
                SensitiveWordManager.detect(detectText, words)
            }
            currentMatches = matches

            // 统计
            val distinctWords = matches.map { it.word }.distinct().size
            binding.tvMatchCount.text = matches.size.toString()
            binding.tvWordTypeCount.text = distinctWords.toString()

            resultAdapter.submitList(matches)
            binding.tvNoResult.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
            binding.rvResults.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // ==================== 词库管理 ====================

    private fun loadWords() {
        lifecycleScope.launch {
            val words = withContext(Dispatchers.IO) {
                SensitiveWordManager.getWords(this@SensitiveWordActivity)
            }
            currentWords = words
            binding.tvLibrarySize.text = words.size.toString()
            wordAdapter.submitList(words)
            binding.tvNoWords.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
            binding.rvWords.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showAddWordDialog() {
        val input = EditText(this).apply {
            hint = "输入敏感词"
            setPadding(48, 32, 48, 16)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("添加敏感词")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val word = input.text?.toString() ?: ""
                if (word.isNotBlank()) {
                    SensitiveWordManager.addWord(this, word)
                    loadWords()
                    runDetection()
                    Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteWord(word: String) {
        SensitiveWordManager.removeWord(this, word)
        loadWords()
        runDetection()
    }

    private fun showClearConfirm() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空词库")
            .setMessage("确定要清空全部词库吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                SensitiveWordManager.clearWords(this)
                loadWords()
                runDetection()
                Toast.makeText(this, "词库已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 导入导出 ====================

    private fun pickImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    private fun pickExportFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "sensitive_words.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data ?: return
        when (requestCode) {
            REQUEST_IMPORT -> handleImport(uri)
            REQUEST_EXPORT -> handleExport(uri)
        }
    }

    private fun handleImport(uri: Uri) {
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                SensitiveWordManager.importFromUri(this@SensitiveWordActivity, uri)
            }
            if (imported.isNullOrEmpty()) {
                Toast.makeText(this@SensitiveWordActivity, "导入失败或文件为空", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // 合并到现有词库
            val existing = SensitiveWordManager.getWords(this@SensitiveWordActivity)
            val merged = (existing + imported).distinct()
            SensitiveWordManager.replaceWords(this@SensitiveWordActivity, merged)
            loadWords()
            runDetection()
            Toast.makeText(
                this@SensitiveWordActivity,
                "已导入 ${imported.size} 个词条（合并去重）",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleExport(uri: Uri) {
        val success = SensitiveWordManager.exportToUri(this, uri)
        Toast.makeText(
            this,
            if (success) "导出成功" else "导出失败",
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val REQUEST_IMPORT = 4001
        private const val REQUEST_EXPORT = 4002

        fun createIntent(context: android.content.Context, title: String, text: String): Intent {
            return Intent(context, SensitiveWordActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
        }
    }

    // ==================== 检测结果适配器 ====================

    inner class ResultAdapter :
        RecyclerView.Adapter<ResultAdapter.ViewHolder>() {

        private val items = mutableListOf<SensitiveWordManager.Match>()

        fun submitList(list: List<SensitiveWordManager.Match>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemSensitiveResultBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSensitiveResultBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val match = items[position]
            holder.binding.tvHitWord.text = match.word
            holder.binding.tvHitPosition.text = "位置 ${match.start}"
            holder.binding.tvHitContext.text = buildHighlightedContext(match)
        }

        override fun getItemCount(): Int = items.size

        /**
         * 在上下文片段中高亮命中的敏感词
         */
        private fun buildHighlightedContext(match: SensitiveWordManager.Match): CharSequence {
            val context = match.context
            val spannable = SpannableString(context)
            // 在 context 中查找命中词并高亮
            var idx = context.indexOf(match.word)
            val highlightColor = ContextCompat.getColor(
                this@SensitiveWordActivity, R.color.sensitive_highlight
            )
            while (idx >= 0) {
                spannable.setSpan(
                    BackgroundColorSpan(highlightColor),
                    idx,
                    idx + match.word.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                idx = context.indexOf(match.word, idx + match.word.length)
            }
            return spannable
        }
    }

    // ==================== 词库适配器 ====================

    inner class WordAdapter(
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<WordAdapter.ViewHolder>() {

        private val items = mutableListOf<String>()

        fun submitList(list: List<String>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemWordBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val word = items[position]
            holder.binding.tvWord.text = word
            holder.binding.btnDeleteWord.setOnClickListener { onDelete(word) }
        }

        override fun getItemCount(): Int = items.size
    }
}
