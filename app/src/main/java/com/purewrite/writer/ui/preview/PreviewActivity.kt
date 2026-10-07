package com.purewrite.writer.ui.preview

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.databinding.ActivityPreviewBinding
import com.purewrite.writer.util.MarkdownParser
import com.purewrite.writer.util.MarkdownRenderer
import com.purewrite.writer.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Markdown 预览页：将章节内容渲染为带格式的可读文本
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private val repository get() = (application as App).repository
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvPreview.textSize = prefs.fontSize.toFloat()
        binding.tvPreview.setLineSpacing(0f, prefs.lineSpacing)

        val bookId = intent.getLongExtra("book_id", -1)
        val chapterId = intent.getLongExtra("chapter_id", -1)

        loadContent(bookId, chapterId)
    }

    private fun loadContent(bookId: Long, chapterId: Long) {
        lifecycleScope.launch {
            if (chapterId > 0) {
                val chapter = repository.getChapterById(chapterId)
                withContext(Dispatchers.Main) {
                    chapter?.let {
                        binding.toolbar.title = it.title
                        val markdown = MarkdownParser.chapterToMarkdown(it)
                        val spannable = MarkdownRenderer.render(markdown)
                        binding.tvPreview.text = spannable
                        binding.tvPreview.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
            } else if (bookId > 0) {
                val book = repository.getBookById(bookId)
                val chapters = repository.getChaptersByBook(bookId)
                withContext(Dispatchers.Main) {
                    book?.let {
                        binding.toolbar.title = it.title
                        val markdown = MarkdownParser.bookToMarkdown(it, chapters)
                        val spannable = MarkdownRenderer.render(markdown)
                        binding.tvPreview.text = spannable
                        binding.tvPreview.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
            }
        }
    }
}
