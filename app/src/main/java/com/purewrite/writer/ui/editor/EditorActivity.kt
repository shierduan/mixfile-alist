package com.purewrite.writer.ui.editor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.databinding.ActivityEditorBinding
import com.purewrite.writer.databinding.DialogReaderSettingsBinding
import com.purewrite.writer.ui.preview.PreviewActivity
import com.purewrite.writer.util.ChinesePunctuationConverter
import com.purewrite.writer.util.NovelFormatter
import com.purewrite.writer.util.PrefsManager
import com.purewrite.writer.util.VoiceToTextHelper
import com.purewrite.writer.util.WordCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private var chapterId: Long = -1
    private var bookId: Long = -1
    private var chapter: ChapterEntity? = null
    private val repository get() = (application as App).repository
    private lateinit var prefs: PrefsManager

    // Undo/Redo history
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var isUndoing = false

    // Auto-save
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable { saveChapter() }
    private var isDirty = false

    private var lastSavedContent = ""

    // 语音转文字
    private lateinit var voiceHelper: VoiceToTextHelper
    private var isListening = false

    // 视图模式：true=阅读模式，false=编辑模式
    private var isReaderMode = false

    // 章节列表用于导航
    private var allChapterIds: List<Long> = emptyList()
    private var currentChapterIndex = -1

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
        setupReaderButtons()
        loadChapter()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.title = getString(R.string.editor)
        binding.toolbar.inflateMenu(R.menu.menu_editor)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_switch_view -> {
                    toggleReaderMode()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupEditor() {
        val fontSize = prefs.fontSize.toFloat()
        binding.etContent.textSize = fontSize
        binding.etContent.setLineSpacing(0f, prefs.lineSpacing)

        binding.etContent.addTextChangedListener(object : TextWatcher {
            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                previousText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (prefs.punctuationConvert && !isUndoing) {
                    handlePunctuationConvert(s, start, before, count)
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

                // 如果在阅读模式，实时更新阅读视图
                if (isReaderMode) {
                    renderReaderView()
                }
            }
        })
    }

    private fun handlePunctuationConvert(s: CharSequence?, start: Int, before: Int, count: Int) {
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

    private fun setupReaderButtons() {
        binding.btnPrevChapter.setOnClickListener { navigateChapter(-1) }
        binding.btnNextChapter.setOnClickListener { navigateChapter(1) }
    }

    // ==================== 视图切换 ====================

    /**
     * 切换编辑/阅读视图
     */
    private fun toggleReaderMode() {
        if (isDirty) saveChapter()
        isReaderMode = !isReaderMode
        if (isReaderMode) {
            enterReaderMode()
        } else {
            exitReaderMode()
        }
    }

    private fun enterReaderMode() {
        binding.svEditor.visibility = View.GONE
        binding.svReader.visibility = View.VISIBLE
        binding.etChapterTitle.visibility = View.GONE
        binding.toolbar.title = getString(R.string.reader_mode)
        // 隐藏编辑相关按钮，显示阅读设置入口
        renderReaderView()
        // 点击阅读区中央弹出设置
        binding.tvReaderContent.setOnClickListener { showReaderSettings() }
        Toast.makeText(this, R.string.reader_mode, Toast.LENGTH_SHORT).show()
    }

    private fun exitReaderMode() {
        binding.svEditor.visibility = View.VISIBLE
        binding.svReader.visibility = View.GONE
        binding.etChapterTitle.visibility = View.VISIBLE
        binding.toolbar.title = getString(R.string.editor)
        Toast.makeText(this, R.string.edit_mode, Toast.LENGTH_SHORT).show()
    }

    /**
     * 渲染阅读视图
     */
    private fun renderReaderView() {
        val ch = chapter ?: return
        val fontSize = prefs.readerFontSize
        val titleSize = (fontSize + 6).coerceAtMost(32)
        val textColor = if (prefs.readerBgTheme == 3) {
            ContextCompat.getColor(this, R.color.reader_text_dark)
        } else {
            ContextCompat.getColor(this, R.color.reader_text_light)
        }
        val titleColor = if (prefs.readerBgTheme == 3) {
            ContextCompat.getColor(this, R.color.reader_title_dark)
        } else {
            ContextCompat.getColor(this, R.color.reader_title)
        }

        // 标题
        binding.tvReaderTitle.text = ch.title.ifBlank { getString(R.string.untitled_chapter) }
        binding.tvReaderTitle.textSize = titleSize.toFloat()
        binding.tvReaderTitle.setTextColor(titleColor)

        // 正文（小说排版）
        val spannable = NovelFormatter.formatChapter(ch, fontSize, titleSize, textColor, titleColor)
        binding.tvReaderContent.text = spannable
        binding.tvReaderContent.textSize = fontSize.toFloat()
        binding.tvReaderContent.setLineSpacing(0f, prefs.readerLineSpacing)
        binding.tvReaderContent.setTextColor(textColor)

        // 背景色
        val bgColor = when (prefs.readerBgTheme) {
            0 -> ContextCompat.getColor(this, R.color.reader_bg_parchment)
            1 -> ContextCompat.getColor(this, R.color.reader_bg_white)
            2 -> ContextCompat.getColor(this, R.color.reader_bg_green)
            3 -> ContextCompat.getColor(this, R.color.reader_bg_dark)
            else -> ContextCompat.getColor(this, R.color.reader_bg_parchment)
        }
        binding.svReader.setBackgroundColor(bgColor)
    }

    /**
     * 显示阅读设置面板
     */
    private fun showReaderSettings() {
        val dialog = BottomSheetDialog(this)
        val settingsBinding = DialogReaderSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(settingsBinding.root)

        // 字号
        val fontProgress = prefs.readerFontSize - 12
        settingsBinding.seekReaderFontSize.progress = fontProgress
        settingsBinding.tvReaderFontSizeValue.text = "${prefs.readerFontSize}sp"
        settingsBinding.seekReaderFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 12
                prefs.readerFontSize = size
                settingsBinding.tvReaderFontSizeValue.text = "${size}sp"
                renderReaderView()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 行距
        val spacingProgress = ((prefs.readerLineSpacing - 1.0f) * 10).toInt()
        settingsBinding.seekReaderLineSpacing.progress = spacingProgress
        settingsBinding.tvReaderLineSpacingValue.text = "%.1f".format(prefs.readerLineSpacing)
        settingsBinding.seekReaderLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = 1.0f + progress / 10.0f
                prefs.readerLineSpacing = spacing
                settingsBinding.tvReaderLineSpacingValue.text = "%.1f".format(spacing)
                renderReaderView()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 背景主题
        settingsBinding.btnBgParchment.setOnClickListener {
            prefs.readerBgTheme = 0
            renderReaderView()
        }
        settingsBinding.btnBgWhite.setOnClickListener {
            prefs.readerBgTheme = 1
            renderReaderView()
        }
        settingsBinding.btnBgGreen.setOnClickListener {
            prefs.readerBgTheme = 2
            renderReaderView()
        }
        settingsBinding.btnBgDark.setOnClickListener {
            prefs.readerBgTheme = 3
            renderReaderView()
        }

        dialog.show()
    }

    /**
     * 章节导航
     */
    private fun navigateChapter(direction: Int) {
        if (allChapterIds.isEmpty() || currentChapterIndex < 0) return
        val newIndex = currentChapterIndex + direction
        if (newIndex < 0 || newIndex >= allChapterIds.size) {
            Toast.makeText(this, R.string.chapter_end, Toast.LENGTH_SHORT).show()
            return
        }
        if (isDirty) saveChapter()
        chapterId = allChapterIds[newIndex]
        currentChapterIndex = newIndex
        loadChapter()
        if (isReaderMode) renderReaderView()
    }

    private fun openPreview() {
        if (isDirty) saveChapter()
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("chapter_id", chapterId)
            putExtra("book_id", bookId)
        }
        startActivity(intent)
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
                if (text.isNotBlank()) {
                    insertVoiceText(text)
                }
            },
            onError = {
                isListening = false
                binding.btnVoice.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.text_secondary)
                )
                Toast.makeText(this, R.string.voice_error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun insertVoiceText(text: String) {
        val pos = binding.etContent.selectionStart.coerceAtLeast(0)
        val editable = binding.etContent.text
        editable?.insert(pos, "$text")
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
            // 加载章节列表用于导航
            if (bookId > 0) {
                val chapters = repository.getChaptersByBook(bookId).first()
                allChapterIds = chapters.map { it.id }
                currentChapterIndex = allChapterIds.indexOf(chapterId)
            }
            withContext(Dispatchers.Main) {
                chapter?.let { ch ->
                    binding.etChapterTitle.setText(ch.title)
                    binding.etContent.setText(ch.content)
                    lastSavedContent = ch.content
                    updateWordCount(ch.content)
                    binding.etContent.setSelection(ch.content.length)
                    if (isReaderMode) renderReaderView()
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
            val currentToday = prefs.todayWords
            prefs.todayWords = currentToday + newWords
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
