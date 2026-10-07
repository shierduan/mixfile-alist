package com.purewrite.writer.ui.reader

import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AlignmentSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.data.db.VolumeEntity
import com.purewrite.writer.databinding.ActivityBookReaderBinding
import com.purewrite.writer.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 书籍级阅读模式：连续渲染整本书的所有章节（按卷顺序 + 章节顺序），
 * 模拟已发布小说的读者视角。
 */
class BookReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookReaderBinding
    private val repository get() = (application as App).repository
    private lateinit var prefs: PrefsManager

    private var bookId: Long = -1L
    private var bookTitle: String = ""

    /** 全书按渲染顺序记录的每个章节标题在 Spannable 中的起始 char offset */
    private val chapterCharOffsets = ArrayList<Int>()
    private var currentChapterIndex = 0
    private var hasContent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        bookId = intent.getLongExtra("book_id", -1L)
        bookTitle = intent.getStringExtra("book_title") ?: getString(R.string.untitled_book)

        setupToolbar()
        applyReaderSettings()
        setupNavigation()
        loadBookContent()
    }

    private fun setupToolbar() {
        binding.toolbar.title = bookTitle
        binding.toolbar.setNavigationOnClickListener { finish() }
        // 菜单项：阅读设置（程序化添加，避免新增 menu 资源文件）
        binding.toolbar.menu.add(
            0, MENU_ID_READER_SETTINGS, 0, getString(R.string.reader_settings)
        ).apply {
            setIcon(R.drawable.ic_reader_settings)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_ID_READER_SETTINGS) {
                showReaderSettings()
                true
            } else {
                false
            }
        }
    }

    private fun applyReaderSettings() {
        binding.tvReaderContent.textSize = prefs.readerFontSize.toFloat()
        binding.tvReaderContent.setLineSpacing(0f, prefs.readerLineSpacing)
    }

    private fun setupNavigation() {
        binding.btnPrevChapter.setOnClickListener {
            if (!hasContent) return@setOnClickListener
            val cur = findCurrentChapterIndex()
            if (cur <= 0) {
                Toast.makeText(this, "已是第一章", Toast.LENGTH_SHORT).show()
            } else {
                goToChapter(cur - 1)
            }
        }
        binding.btnNextChapter.setOnClickListener {
            if (!hasContent) return@setOnClickListener
            val cur = findCurrentChapterIndex()
            if (cur >= chapterCharOffsets.size - 1) {
                Toast.makeText(this, "已是最后一章", Toast.LENGTH_SHORT).show()
            } else {
                goToChapter(cur + 1)
            }
        }
    }

    private fun loadBookContent() {
        lifecycleScope.launch {
            val chapters = repository.getChaptersByBook(bookId)
            val volumes = repository.getVolumesByBookList(bookId)
            val spannable = withContext(Dispatchers.Default) {
                buildBookContent(chapters, volumes)
            }
            withContext(Dispatchers.Main) {
                if (chapterCharOffsets.isEmpty()) {
                    binding.tvReaderContent.text = getString(R.string.empty_chapters)
                    hasContent = false
                } else {
                    binding.tvReaderContent.text = spannable
                    hasContent = true
                    currentChapterIndex = 0
                    binding.tvReaderContent.post { goToChapter(0) }
                }
            }
        }
    }

    /**
     * 构建整本书的 Spannable 内容：按卷顺序 + 章节顺序，
     * 卷名作为居中大字号的分隔标题，章节标题居中加粗。
     */
    private fun buildBookContent(
        chapters: List<ChapterEntity>,
        volumes: List<VolumeEntity>
    ): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        chapterCharOffsets.clear()

        val volumesOrdered = volumes.sortedBy { it.order }
        val chaptersByVolume = LinkedHashMap<Long, MutableList<ChapterEntity>>()
        for (ch in chapters) {
            chaptersByVolume.getOrPut(ch.volumeId) { ArrayList() }.add(ch)
        }
        for (list in chaptersByVolume.values) list.sortBy { it.order }

        for (vol in volumesOrdered) {
            val list = chaptersByVolume.remove(vol.id)
            if (list.isNullOrEmpty()) continue
            appendVolumeHeader(ssb, vol.title.ifBlank { "未命名卷" })
            for (ch in list) appendChapter(ssb, ch)
            ssb.append("\n")
        }

        // 容错：理论上不会出现（章节通过外键归属卷），仅作防御
        if (chaptersByVolume.isNotEmpty()) {
            val orphans = chaptersByVolume.values.flatten().sortedBy { it.order }
            appendVolumeHeader(ssb, "其他章节")
            for (ch in orphans) appendChapter(ssb, ch)
        }

        return ssb
    }

    private fun appendVolumeHeader(ssb: SpannableStringBuilder, title: String) {
        val start = ssb.length
        ssb.append(title)
        ssb.append("\n")
        val end = ssb.length
        ssb.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(RelativeSizeSpan(1.6f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append("\n")
    }

    private fun appendChapter(ssb: SpannableStringBuilder, chapter: ChapterEntity) {
        val titleStart = ssb.length
        chapterCharOffsets.add(titleStart)
        val title = chapter.title.ifBlank { getString(R.string.untitled_chapter) }
        ssb.append(title)
        ssb.append("\n")
        val titleEnd = ssb.length
        ssb.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            titleStart, titleEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        ssb.setSpan(StyleSpan(Typeface.BOLD), titleStart, titleEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(RelativeSizeSpan(1.2f), titleStart, titleEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append("\n")

        val paragraphs = chapter.content
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) {
            ssb.append("\u3000\u3000").append("（本章暂无内容）").append("\n\n")
        } else {
            for (para in paragraphs) {
                ssb.append("\u3000\u3000").append(para).append("\n\n")
            }
        }
    }

    /** 根据当前 ScrollView 滚动位置，定位当前阅读的章节索引 */
    private fun findCurrentChapterIndex(): Int {
        if (chapterCharOffsets.isEmpty()) return 0
        val layout = binding.tvReaderContent.layout ?: return currentChapterIndex
        val scrollY = binding.svReader.scrollY
        val pad = binding.tvReaderContent.paddingTop
        var current = 0
        chapterCharOffsets.forEachIndexed { i, charOffset ->
            val line = try {
                layout.getLineForOffset(charOffset)
            } catch (e: Exception) {
                return@forEachIndexed
            }
            val y = layout.getLineTop(line) + pad
            if (y <= scrollY + 1) current = i
        }
        currentChapterIndex = current
        return current
    }

    /** 滚动到指定章节：将章节起始 char offset 转为像素 y，调用 svReader.scrollTo */
    private fun goToChapter(index: Int) {
        if (index !in chapterCharOffsets.indices) return
        currentChapterIndex = index
        val tv = binding.tvReaderContent
        val charOffset = chapterCharOffsets[index]
        val scrollTo = Runnable {
            val l = tv.layout ?: return@Runnable
            val line = l.getLineForOffset(charOffset)
            val y = l.getLineTop(line) + tv.paddingTop
            binding.svReader.scrollTo(0, y.coerceAtLeast(0))
        }
        if (tv.layout == null) tv.post(scrollTo) else scrollTo.run()
    }

    /** 底部弹出面板：调节字号与行距（无背景主题切换） */
    private fun showReaderSettings() {
        val dialog = BottomSheetDialog(this)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(getColor(R.color.bg_card))
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.reader_settings)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.text_primary))
        })

        // 字号 12-32sp
        val fontRow = buildSliderRow(
            dp = dp,
            label = getString(R.string.reader_font_size),
            progress = prefs.readerFontSize - FONT_SIZE_MIN,
            max = FONT_SIZE_MAX - FONT_SIZE_MIN,
            valueText = "${prefs.readerFontSize}sp"
        )
        container.addView(fontRow.root)

        // 行距 1.0-3.5，步长 0.1
        val lineRow = buildSliderRow(
            dp = dp,
            label = getString(R.string.reader_line_spacing),
            progress = ((prefs.readerLineSpacing - LINE_SPACING_MIN) / LINE_SPACING_STEP).roundToInt(),
            max = LINE_SPACING_STEPS,
            valueText = String.format(Locale.getDefault(), "%.1fx", prefs.readerLineSpacing)
        )
        container.addView(lineRow.root)

        fontRow.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = FONT_SIZE_MIN + progress
                fontRow.valueView.text = "${size}sp"
                binding.tvReaderContent.textSize = size.toFloat()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.readerFontSize = FONT_SIZE_MIN + (seekBar?.progress ?: 0)
            }
        })

        lineRow.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sp = LINE_SPACING_MIN + progress * LINE_SPACING_STEP
                lineRow.valueView.text = String.format(Locale.getDefault(), "%.1fx", sp)
                binding.tvReaderContent.setLineSpacing(0f, sp)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sp = LINE_SPACING_MIN + (seekBar?.progress ?: 0) * LINE_SPACING_STEP
                prefs.readerLineSpacing = sp
            }
        })

        dialog.setContentView(container)
        dialog.show()
    }

    private data class SliderRow(
        val root: View,
        val seekBar: SeekBar,
        val valueView: TextView
    )

    private fun buildSliderRow(
        dp: Float,
        label: String,
        progress: Int,
        max: Int,
        valueText: String
    ): SliderRow {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val seek = SeekBar(this).apply {
            this.max = max
            this.progress = progress
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            layoutParams = lp
        }
        val valView = TextView(this).apply {
            text = valueText
            setTextColor(getColor(R.color.primary))
            textSize = 14f
            minWidth = (56 * dp).toInt()
        }
        row.addView(labelView)
        row.addView(seek)
        row.addView(valView)
        return SliderRow(row, seek, valView)
    }

    companion object {
        private const val MENU_ID_READER_SETTINGS = 1001
        private const val FONT_SIZE_MIN = 12
        private const val FONT_SIZE_MAX = 32
        private const val LINE_SPACING_MIN = 1.0f
        private const val LINE_SPACING_MAX = 3.5f
        private const val LINE_SPACING_STEP = 0.1f
        private const val LINE_SPACING_STEPS = 25 // (3.5 - 1.0) / 0.1
    }
}
