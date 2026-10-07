package com.purewrite.writer.ui.chapters

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.databinding.ActivityChaptersBinding
import com.purewrite.writer.databinding.DialogNewChapterBinding
import com.purewrite.writer.databinding.ItemChapterBinding
import com.purewrite.writer.ui.editor.EditorActivity
import com.purewrite.writer.util.ExportUtils
import com.purewrite.writer.util.TimeUtils
import com.purewrite.writer.util.WordCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChapterListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChaptersBinding
    private lateinit var adapter: ChapterAdapter
    private var bookId: Long = -1
    private var bookTitle: String = ""
    private val repository get() = (application as App).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChaptersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getLongExtra("book_id", -1)
        bookTitle = intent.getStringExtra("book_title") ?: ""

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadBookInfo()
        loadChapters()
    }

    private fun setupToolbar() {
        binding.toolbar.title = bookTitle
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_chapters)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export -> exportBook()
                R.id.action_stats -> { /* stats for book */ }
            }
            true
        }
    }

    private fun setupRecyclerView() {
        adapter = ChapterAdapter(
            onChapterClick = { chapter -> openChapter(chapter) },
            onChapterMore = { chapter, view -> showChapterMenu(chapter, view) }
        )
        binding.rvChapters.layoutManager = LinearLayoutManager(this)
        binding.rvChapters.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddChapter.setOnClickListener { showNewChapterDialog() }
    }

    private fun loadBookInfo() {
        binding.tvBookTitle.text = bookTitle
        lifecycleScope.launch {
            val count = repository.getChapterCount(bookId)
            val words = repository.getTotalWords(bookId)
            withContext(Dispatchers.Main) {
                binding.tvBookStats.text = getString(
                    R.string.empty_chapters,
                    count, words
                ).let { "${getString(R.string.chapter_count)} $count · ${getString(R.string.total_words)} $words" }
            }
        }
    }

    private fun loadChapters() {
        lifecycleScope.launch {
            repository.getChaptersByBook(bookId).collectLatest { chapters ->
                adapter.submitList(chapters)
                binding.tvEmpty.visibility = if (chapters.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openChapter(chapter: ChapterEntity) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("chapter_id", chapter.id)
            putExtra("book_id", bookId)
        }
        startActivity(intent)
    }

    private fun showNewChapterDialog() {
        val dialogBinding = DialogNewChapterBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.etChapterTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    createChapter(title)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createChapter(title: String) {
        lifecycleScope.launch {
            val order = repository.getChapterCount(bookId)
            val chapter = ChapterEntity(
                bookId = bookId,
                title = title,
                order = order
            )
            val id = repository.insertChapter(chapter)
            // Open the new chapter for editing
            val intent = Intent(this@ChapterListActivity, EditorActivity::class.java).apply {
                putExtra("chapter_id", id)
                putExtra("book_id", bookId)
            }
            startActivity(intent)
        }
    }

    private fun showChapterMenu(chapter: ChapterEntity, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_chapter_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> showRenameDialog(chapter)
                R.id.action_export -> exportChapter(chapter)
                R.id.action_delete -> showDeleteConfirm(chapter)
            }
            true
        }
        popup.show()
    }

    private fun showRenameDialog(chapter: ChapterEntity) {
        val dialogBinding = DialogNewChapterBinding.inflate(layoutInflater)
        dialogBinding.etChapterTitle.setText(chapter.title)
        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.etChapterTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.updateChapter(chapter.copy(title = title, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirm(chapter: ChapterEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(R.string.delete_chapter_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteChapter(chapter)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportBook() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "$bookTitle.txt")
        }
        startActivityForResult(intent, REQUEST_EXPORT_BOOK)
    }

    private fun exportChapter(chapter: ChapterEntity) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "${chapter.title}.txt")
        }
        currentExportChapter = chapter
        startActivityForResult(intent, REQUEST_EXPORT_CHAPTER)
    }

    private var currentExportChapter: ChapterEntity? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQUEST_EXPORT_BOOK -> {
                lifecycleScope.launch {
                    val book = repository.getBookById(bookId) ?: return@launch
                    val chapters = repository.getChaptersByBook(bookId).first()
                    val success = ExportUtils.exportBookToTxt(this@ChapterListActivity, uri, book, chapters)
                    withContext(Dispatchers.Main) {
                        showToast(if (success) R.string.export_success else R.string.export_failed)
                    }
                }
            }
            REQUEST_EXPORT_CHAPTER -> {
                currentExportChapter?.let { chapter ->
                    val success = ExportUtils.exportChapterToTxt(this, uri, chapter)
                    showToast(if (success) R.string.export_success else R.string.export_failed)
                }
            }
        }
    }

    private fun showToast(resId: Int) {
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_EXPORT_BOOK = 2001
        private const val REQUEST_EXPORT_CHAPTER = 2002
    }
}

class ChapterAdapter(
    private val onChapterClick: (ChapterEntity) -> Unit,
    private val onChapterMore: (ChapterEntity, View) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    private var chapters: List<ChapterEntity> = emptyList()

    fun submitList(list: List<ChapterEntity>) {
        chapters = list
        notifyDataSetChanged()
    }

    inner class ChapterViewHolder(val binding: ItemChapterBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = ItemChapterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters[position]
        holder.binding.tvChapterIndex.text = (position + 1).toString()
        holder.binding.tvChapterTitle.text = chapter.title
        holder.binding.tvChapterPreview.text = chapter.content.ifBlank { "（空章节）" }
        holder.binding.tvChapterWords.text = "${holder.itemView.context.getString(R.string.word_count)} ${chapter.wordCount}"
        holder.binding.tvChapterUpdated.text = TimeUtils.getFriendlyTime(chapter.updatedAt)

        holder.itemView.setOnClickListener { onChapterClick(chapter) }
    }

    override fun getItemCount() = chapters.size
}
