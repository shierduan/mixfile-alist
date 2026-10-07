package com.purewrite.writer.ui.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.databinding.ActivityRecycleBinBinding
import com.purewrite.writer.databinding.ItemRecycleBinBinding
import com.purewrite.writer.util.AutoBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 回收站：查看和恢复已删除的书籍、章节
 */
class RecycleBinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecycleBinBinding
    private val repository get() = (application as App).repository

    private val bookAdapter = DeletedItemAdapter<BookEntity>(
        getTitle = { it.title },
        getSubtitle = { it.author.ifEmpty { "无作者" } + " · 删除于 " + AutoBackupManager.formatTime(it.deletedAt) },
        onRestore = { book -> restoreBook(book) },
        onDeleteForever = { book -> deleteBookForever(book) }
    )

    private val chapterAdapter = DeletedItemAdapter<ChapterEntity>(
        getTitle = { it.title },
        getSubtitle = { "${it.wordCount} 字 · 删除于 " + AutoBackupManager.formatTime(it.deletedAt) },
        onRestore = { chapter -> restoreChapter(chapter) },
        onDeleteForever = { chapter -> deleteChapterForever(chapter) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecycleBinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvDeletedBooks.layoutManager = LinearLayoutManager(this)
        binding.rvDeletedBooks.adapter = bookAdapter

        binding.rvDeletedChapters.layoutManager = LinearLayoutManager(this)
        binding.rvDeletedChapters.adapter = chapterAdapter

        loadDeletedItems()
    }

    private fun loadDeletedItems() {
        lifecycleScope.launch {
            val books = repository.getDeletedBooks()
            val chapters = repository.getDeletedChapters()
            withContext(Dispatchers.Main) {
                bookAdapter.submitList(books)
                chapterAdapter.submitList(chapters)
                binding.tvNoBooks.visibility = if (books.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                binding.rvDeletedBooks.visibility = if (books.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                binding.tvNoChapters.visibility = if (chapters.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                binding.rvDeletedChapters.visibility = if (chapters.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
    }

    private fun restoreBook(book: BookEntity) {
        lifecycleScope.launch {
            repository.restoreBook(book.id)
            // 恢复该书的卷和章节
            val volumes = repository.getDeletedVolumes().filter { it.bookId == book.id }
            for (v in volumes) {
                repository.restoreVolume(v.id)
                val chapters = repository.getDeletedChapters().filter { it.volumeId == v.id }
                for (c in chapters) repository.restoreChapter(c.id)
            }
            Toast.makeText(this@RecycleBinActivity, "已恢复", Toast.LENGTH_SHORT).show()
            loadDeletedItems()
        }
    }

    private fun deleteBookForever(book: BookEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("彻底删除")
            .setMessage("将永久删除《${book.title}》及其所有卷和章节，此操作不可恢复。是否确认？")
            .setPositiveButton("彻底删除") { _, _ ->
                lifecycleScope.launch {
                    repository.permanentDeleteBook(book.id)
                    Toast.makeText(this@RecycleBinActivity, "已彻底删除", Toast.LENGTH_SHORT).show()
                    loadDeletedItems()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun restoreChapter(chapter: ChapterEntity) {
        lifecycleScope.launch {
            repository.restoreChapter(chapter.id)
            Toast.makeText(this@RecycleBinActivity, "已恢复", Toast.LENGTH_SHORT).show()
            loadDeletedItems()
        }
    }

    private fun deleteChapterForever(chapter: ChapterEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("彻底删除")
            .setMessage("将永久删除章节《${chapter.title}》，此操作不可恢复。是否确认？")
            .setPositiveButton("彻底删除") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteChapterById(chapter.id)
                    Toast.makeText(this@RecycleBinActivity, "已彻底删除", Toast.LENGTH_SHORT).show()
                    loadDeletedItems()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============ 通用适配器 ============
    inner class DeletedItemAdapter<T>(
        private val getTitle: (T) -> String,
        private val getSubtitle: (T) -> String,
        private val onRestore: (T) -> Unit,
        private val onDeleteForever: (T) -> Unit
    ) : RecyclerView.Adapter<DeletedItemAdapter<T>.ViewHolder>() {

        private val items = mutableListOf<T>()

        fun submitList(list: List<T>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemRecycleBinBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRecycleBinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvTitle.text = getTitle(item)
            holder.binding.tvSubtitle.text = getSubtitle(item)
            holder.binding.btnRestore.setOnClickListener { onRestore(item) }
            holder.binding.btnDeleteForever.setOnClickListener { onDeleteForever(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
