package com.purewrite.writer.ui.editor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.databinding.ActivityEditorBinding
import com.purewrite.writer.ui.preview.PreviewActivity
import com.purewrite.writer.ui.sensitive.SensitiveWordActivity
import com.purewrite.writer.util.ChinesePunctuationConverter
import com.purewrite.writer.util.PrefsManager
import com.purewrite.writer.util.VoiceToTextHelper
import com.purewrite.writer.util.WordCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 章节编辑器：纯文本编辑，含标点转换、撤销重做、语音输入、Markdown 预览
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private var chapterId: Long = -1
    private var bookId: Long = -1
    private var chapter: ChapterEntity? = null
    private val repository get() = (application as App).repository
    private lateinit var prefs: PrefsManager

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var isUndoing = false

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable { saveChapter() }
    private var isDirty = false

    private var voiceProgressDialog: com.google.android.material.dialog.MaterialAlertDialogBuilder? = null
    private var voiceDialog: androidx.appcompat.app.AlertDialog? = null

    private lateinit var voiceHelper: VoiceToTextHelper
    private var isListening = false
    private var lastSavedContent = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chapterId = intent.getLongExtra("chapter_id", -1)
        bookId = intent.getLongExtra("book_id", -1)
        prefs = PrefsManager(this)
        voiceHelper = VoiceToTextHelper(this)

        setupToolbar()
        setupEditor()
        setupButtons()
        loadChapter()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.title = getString(R.string.editor)
        binding.toolbar.inflateMenu(R.menu.menu_editor)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_switch_view -> {
                    openPreview()
                    true
                }
                R.id.action_find_replace -> {
                    showFindReplaceDialog()
                    true
                }
                R.id.action_sensitive_check -> {
                    startSensitiveCheck()
                    true
                }
                else -> false
            }
        }
    }

    /** 查找与替换对话框 */
    private fun showFindReplaceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_find_replace, null)
        val etFind = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFind)
        val etReplace = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReplace)
        val tvResult = dialogView.findViewById<android.widget.TextView>(R.id.tvFindResult)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("查找与替换")
            .setView(dialogView)
            .setPositiveButton("全部替换") { _, _ ->
                val find = etFind.text?.toString() ?: ""
                val replace = etReplace.text?.toString() ?: ""
                if (find.isBlank()) return@setPositiveButton
                val content = binding.etContent.text?.toString() ?: ""
                val count = content.windowed(find.length, 1, partialWindows = false).count { it == find }
                if (count == 0) {
                    tvResult.text = "未找到匹配内容"
                    return@setPositiveButton
                }
                val newContent = content.replace(find, replace)
                binding.etContent.setText(newContent)
                android.widget.Toast.makeText(this, "已替换 $count 处", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("查找下一个") { _, _ ->
                val find = etFind.text?.toString() ?: ""
                if (find.isBlank()) return@setNegativeButton
                findNext(find)
            }
            .setNeutralButton("关闭", null)
            .show()
    }

    /** 查找下一个匹配项并定位光标 */
    private fun findNext(query: String) {
        val content = binding.etContent.text?.toString() ?: ""
        val selectionStart = binding.etContent.selectionStart.coerceAtLeast(0)
        // 从当前光标之后查找
        val index = content.indexOf(query, selectionStart + 1)
        val targetIndex = if (index >= 0) index else content.indexOf(query)
        if (targetIndex >= 0) {
            binding.etContent.setSelection(targetIndex, targetIndex + query.length)
        } else {
            android.widget.Toast.makeText(this, "未找到匹配内容", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEditor() {
        binding.etContent.textSize = prefs.fontSize.toFloat()
        binding.etContent.setLineSpacing(0f, prefs.lineSpacing)

        binding.etContent.addTextChangedListener(object : TextWatcher {
            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                previousText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (prefs.punctuationConvert && !isUndoing) {
                    handlePunctuationConvert(s, start, count)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                if (!isUndoing && previousText != text) {
                    if (undoStack.size > 50) undoStack.removeFirst()
                    undoStack.addLast(previousText)
                    redoStack.clear()
                }
                isUndoing = false
                updateWordCount(text)
                isDirty = true

                if (prefs.autoSave) {
                    autoSaveHandler.removeCallbacks(autoSaveRunnable)
                    autoSaveHandler.postDelayed(autoSaveRunnable, 1500)
                }
            }
        })
    }

    private fun handlePunctuationConvert(s: CharSequence?, start: Int, count: Int) {
        if (s == null || count != 1) return
        val inserted = s[start]
        val prevChar = if (start > 0) s[start - 1] else null

        if (inserted == '"' || inserted == '\'') {
            val converted = ChinesePunctuationConverter.convertQuote(prevChar, inserted)
            binding.etContent.text?.replace(start, start + 1, converted.toString())
            return
        }

        if (ChinesePunctuationConverter.shouldConvert(prevChar, inserted)) {
            val converted = ChinesePunctuationConverter.convertChar(inserted)
            if (converted != null) {
                binding.etContent.text?.replace(start, start + 1, converted)
            }
        }
    }

    private fun setupButtons() {
        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }
        binding.btnConvert.setOnClickListener { convertAllPunctuation() }
        binding.btnStats.setOnClickListener { showStatsToast() }
        binding.btnSave.setOnClickListener { saveChapter() }
        binding.btnPreview.setOnClickListener { openPreview() }
        binding.btnVoice.setOnClickListener { toggleVoiceInput() }
    }

    private fun openPreview() {
        if (isDirty) saveChapter()
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("chapter_id", chapterId)
            putExtra("book_id", bookId)
        }
        startActivity(intent)
    }

    /** 启动当前章节的敏感词检测 */
    private fun startSensitiveCheck() {
        val title = binding.etChapterTitle.text?.toString()?.trim().orEmpty()
        val content = binding.etContent.text?.toString().orEmpty()
        val displayTitle = title.ifEmpty { getString(R.string.untitled_chapter) }
        startActivity(SensitiveWordActivity.createIntent(this, displayTitle, content))
    }

    private fun toggleVoiceInput() {
        if (isListening) {
            voiceHelper.stopListening()
            isListening = false
            binding.btnVoice.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        } else {
            startVoiceInput()
        }
    }

    private fun startVoiceInput() {
        if (!voiceHelper.hasRecordPermission()) {
            voiceHelper.requestPermission(this, REQUEST_RECORD_AUDIO)
            return
        }
        // 检查模型是否就绪，未就绪则提示下载
        if (!voiceHelper.isModelReady()) {
            promptDownloadModel()
            return
        }
        beginListening()
    }

    /** 显示下载模型对话框 */
    private fun promptDownloadModel() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.voice_model_needed_title))
            .setMessage(getString(R.string.voice_model_needed_message))
            .setPositiveButton(getString(R.string.download)) { _, _ ->
                downloadModel()
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    /** 下载 Vosk 中文模型（带进度） */
    private fun downloadModel() {
        // 自定义进度对话框
        val progressView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
        }
        val statusText = android.widget.TextView(this).apply {
            text = "准备下载…"
            setTextColor(ContextCompat.getColor(this@EditorActivity, R.color.text_secondary))
            textSize = 13f
            setPadding(0, 12, 0, 0)
        }
        progressView.addView(progressBar)
        progressView.addView(statusText)

        voiceDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.voice_downloading_model))
            .setView(progressView)
            .setCancelable(false)
            .setNegativeButton(getString(android.R.string.cancel)) { _, _ ->
                lifecycleScope.launch {
                    // 用户取消后不做删除，下次可继续
                    Toast.makeText(this@EditorActivity, R.string.voice_download_canceled, Toast.LENGTH_SHORT).show()
                }
            }
            .show()

        lifecycleScope.launch {
            val ok = com.purewrite.writer.util.VoskModelManager.downloadAndExtract(this@EditorActivity) { percent ->
                runOnUiThread {
                    progressBar.progress = percent
                    statusText.text = when {
                        percent < 100 -> "下载中 $percent%"
                        else -> "正在解压…"
                    }
                }
            }
            voiceDialog?.dismiss()
            if (ok) {
                Toast.makeText(this@EditorActivity, R.string.voice_model_ready, Toast.LENGTH_SHORT).show()
                beginListening()
            } else {
                Toast.makeText(this@EditorActivity, R.string.voice_download_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 真正开始监听 */
    private fun beginListening() {
        isListening = true
        binding.btnVoice.imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.accent)
        )
        Toast.makeText(this, R.string.voice_tap_to_speak, Toast.LENGTH_SHORT).show()

        voiceHelper.startListening(
            onResult = { text ->
                isListening = false
                binding.btnVoice.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.text_secondary)
                )
                if (text.isNotBlank()) insertVoiceText(text)
            },
            onError = { msg ->
                isListening = false
                binding.btnVoice.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.text_secondary)
                )
                if (msg == "MODEL_NOT_READY") {
                    promptDownloadModel()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onTimeout = {
                isListening = false
                binding.btnVoice.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.text_secondary)
                )
            }
        )
    }

    private fun insertVoiceText(text: String) {
        val pos = binding.etContent.selectionStart.coerceAtLeast(0)
        binding.etContent.text?.insert(pos, text)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startVoiceInput()
            } else {
                Toast.makeText(this, R.string.voice_permission_needed, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 3001
    }

    private fun loadChapter() {
        lifecycleScope.launch {
            chapter = repository.getChapterById(chapterId)
            withContext(Dispatchers.Main) {
                chapter?.let { ch ->
                    binding.etChapterTitle.setText(ch.title)
                    binding.etContent.setText(ch.content)
                    lastSavedContent = ch.content
                    updateWordCount(ch.content)
                    binding.etContent.setSelection(ch.content.length)
                }
            }
        }
    }

    private fun updateWordCount(text: String) {
        val chineseChars = WordCounter.countChineseChars(text)
        val totalWords = WordCounter.countWords(text)
        binding.tvWordCount.text = "$chineseChars / $totalWords"
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        isUndoing = true
        val current = binding.etContent.text.toString()
        redoStack.addLast(current)
        val previous = undoStack.removeLast()
        binding.etContent.setText(previous)
        binding.etContent.setSelection(previous.length)
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        isUndoing = true
        val current = binding.etContent.text.toString()
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        binding.etContent.setText(next)
        binding.etContent.setSelection(next.length)
    }

    private fun convertAllPunctuation() {
        val text = binding.etContent.text.toString()
        val converted = ChinesePunctuationConverter.convertText(text)
        binding.etContent.setText(converted)
        binding.etContent.setSelection(converted.length)
        Toast.makeText(this, R.string.punctuation_convert, Toast.LENGTH_SHORT).show()
    }

    private fun showStatsToast() {
        val text = binding.etContent.text.toString()
        val chinese = WordCounter.countChineseChars(text)
        val total = WordCounter.countWords(text)
        val paragraphs = WordCounter.countParagraphs(text)
        val reading = WordCounter.estimateReadingTime(text)
        val msg = "${getString(R.string.word_count_chinese)}: $chinese\n" +
                "${getString(R.string.word_count_all)}: $total\n" +
                "${getString(R.string.paragraph_count)}: $paragraphs\n" +
                "${getString(R.string.reading_time)}: $reading ${getString(R.string.minutes)}"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun saveChapter() {
        val title = binding.etChapterTitle.text.toString().trim()
            .ifEmpty { getString(R.string.untitled_chapter) }
        val content = binding.etContent.text.toString()
        val wordCount = WordCounter.countWords(content)

        val lastWordCount = WordCounter.countWords(lastSavedContent)
        val newWords = wordCount - lastWordCount
        if (newWords > 0) {
            prefs.todayWords = prefs.todayWords + newWords
        }

        lifecycleScope.launch {
            chapter?.let { ch ->
                val updated = ch.copy(
                    title = title,
                    content = content,
                    wordCount = wordCount,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateChapter(updated)
                chapter = updated
                lastSavedContent = content
                isDirty = false
                // 自动备份
                if (prefs.autoBackup) {
                    com.purewrite.writer.util.AutoBackupManager.createInternalBackup(this@EditorActivity)
                }
                withContext(Dispatchers.Main) {
                    updateWordCount(content)
                    Toast.makeText(this@EditorActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        if (isDirty) saveChapter()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        voiceHelper.destroy()
    }
}
