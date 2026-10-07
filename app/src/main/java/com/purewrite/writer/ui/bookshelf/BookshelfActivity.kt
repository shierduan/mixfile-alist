package com.purewrite.writer.ui.bookshelf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.databinding.ActivityBookshelfBinding
import com.purewrite.writer.databinding.DialogNewBookBinding
import com.purewrite.writer.databinding.ItemBookBinding
import com.purewrite.writer.ui.chapters.ChapterListActivity
import com.purewrite.writer.ui.settings.SettingsActivity
import com.purewrite.writer.ui.stats.StatsActivity
import com.purewrite.writer.util.TimeUtils
import com.purewrite.writer.util.WordCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookshelfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookshelfBinding
    private lateinit var adapter: BookAdapter
    private val repository get() = (application as App).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookshelfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()

        loadBooks()
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_bookshelf)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import -> {
                    startImportFile()
                    true
                }
                R.id.action_stats -> {
                    startActivity(Intent(this, StatsActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 选择文件导入
     */
    private fun startImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/markdown", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    /**
     * 处理导入文件
     */
    private fun handleImport(uri: Uri) {
        lifecycleScope.launch {
            val result = com.purewrite.writer.util.ImportUtils.importFromFile(this@BookshelfActivity, uri)
            if (result == null) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@BookshelfActivity, R.string.import_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }
            // 创建书籍
            val book = BookEntity(title = result.bookTitle)
            val bookId = repository.insertBook(book)
            // 创建默认卷
            val volume = com.purewrite.writer.data.db.VolumeEntity(bookId = bookId, title = "正文")
            val volumeId = repository.insertVolume(volume)
            // 创建章节
            result.chapters.forEach { ch ->
                repository.insertChapter(ch.copy(volumeId = volumeId))
            }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@BookshelfActivity,
                    getString(R.string.import_success) + "：${result.bookTitle}（${result.chapters.size} 章）",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = BookAdapter(
            scope = lifecycleScope,
            onBookClick = { book -> openBook(book) },
            onBookMore = { book, view -> showBookMenu(book, view) }
        )
        binding.rvBooks.layoutManager = LinearLayoutManager(this)
        binding.rvBooks.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener { showNewBookDialog() }
    }

    private fun loadBooks() {
        lifecycleScope.launch {
            repository.getAllBooks().collectLatest { books ->
                adapter.submitList(books)
                binding.tvEmpty.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openBook(book: BookEntity) {
        lifecycleScope.launch {
            repository.updateLastOpened(book.id)
        }
        val intent = Intent(this, ChapterListActivity::class.java).apply {
            putExtra("book_id", book.id)
            putExtra("book_title", book.title)
        }
        startActivity(intent)
    }

    private fun showBookMenu(book: BookEntity, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_book_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> showEditBookDialog(book)
                R.id.action_export_txt -> exportBook(book, false)
                R.id.action_export_md -> exportBook(book, true)
                R.id.action_delete -> showDeleteConfirm(book)
            }
            true
        }
        popup.show()
    }

    private fun showNewBookDialog() {
        val dialogBinding = DialogNewBookBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.etBookTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    val author = dialogBinding.etAuthor.text.toString().trim()
                    val desc = dialogBinding.etDescription.text.toString().trim()
                    createBook(title, author, desc)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditBookDialog(book: BookEntity) {
        val dialogBinding = DialogNewBookBinding.inflate(layoutInflater)
        dialogBinding.etBookTitle.setText(book.title)
        dialogBinding.etAuthor.setText(book.author)
        dialogBinding.etDescription.setText(book.description)
        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.etBookTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    val author = dialogBinding.etAuthor.text.toString().trim()
                    val desc = dialogBinding.etDescription.text.toString().trim()
                    updateBook(book.copy(title = title, author = author, description = desc))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createBook(title: String, author: String, description: String) {
        lifecycleScope.launch {
            val book = BookEntity(
                title = title,
                author = author,
                description = description
            )
            repository.insertBook(book)
        }
    }

    private fun updateBook(book: BookEntity) {
        lifecycleScope.launch {
            repository.updateBook(book.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private var pendingExportBook: BookEntity? = null
    private var pendingExportIsMarkdown: Boolean = false

    private fun exportBook(book: BookEntity, asMarkdown: Boolean) {
        pendingExportBook = book
        pendingExportIsMarkdown = asMarkdown
        val ext = if (asMarkdown) "md" else "txt"
        val mime = if (asMarkdown) "text/markdown" else "text/plain"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, "${book.title}.$ext")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQUEST_IMPORT -> handleImport(uri)
            REQUEST_EXPORT -> {
                val book = pendingExportBook ?: return
                val asMd = pendingExportIsMarkdown
                lifecycleScope.launch {
                    val chapters = repository.getChaptersByBook(book.id)
                    val success = if (asMd) {
                        com.purewrite.writer.util.ExportUtils.exportBookToMarkdown(
                            this@BookshelfActivity, uri, book, chapters
                        )
                    } else {
                        com.purewrite.writer.util.ExportUtils.exportBookToTxt(
                            this@BookshelfActivity, uri, book, chapters
                        )
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            this@BookshelfActivity,
                            if (success) R.string.export_success else R.string.export_failed,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun showDeleteConfirm(book: BookEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(R.string.delete_book_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteBook(book)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val REQUEST_IMPORT = 1000
        private const val REQUEST_EXPORT = 1001
    }
}

class BookAdapter(
    private val scope: CoroutineScope,
    private val onBookClick: (BookEntity) -> Unit,
    private val onBookMore: (BookEntity, View) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    private var books: List<BookEntity> = emptyList()

    fun submitList(list: List<BookEntity>) {
        books = list
        notifyDataSetChanged()
    }

    inner class BookViewHolder(val binding: ItemBookBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.binding.tvTitle.text = book.title
        if (book.author.isNotBlank()) {
            holder.binding.tvAuthor.text = book.author
            holder.binding.tvAuthor.visibility = View.VISIBLE
        }
        holder.binding.vCover.setBackgroundColor(book.coverColor)
        holder.binding.tvUpdated.text = TimeUtils.getFriendlyTime(book.updatedAt)

        // Load stats asynchronously
        val context = holder.itemView.context
        val repo = (context.applicationContext as App).repository
        scope.launch {
            val volCount = repo.getVolumeCount(book.id)
            val chCount = repo.getChapterCountByBook(book.id)
            val words = repo.getTotalWordsByBook(book.id)
            withContext(Dispatchers.Main) {
                holder.binding.tvStats.text =
                    "${context.getString(R.string.volume)} $volCount · ${context.getString(R.string.chapter_count)} $chCount · ${context.getString(R.string.word_count)} $words"
            }
        }

        holder.itemView.setOnClickListener { onBookClick(book) }
        holder.binding.btnMore.setOnClickListener { onBookMore(book, it) }
    }

    override fun getItemCount() = books.size
}
