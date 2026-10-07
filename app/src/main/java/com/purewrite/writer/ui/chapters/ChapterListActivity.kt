package com.purewrite.writer.ui.chapters

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.data.db.VolumeEntity
import com.purewrite.writer.databinding.ActivityChaptersBinding
import com.purewrite.writer.databinding.DialogNewChapterBinding
import com.purewrite.writer.databinding.DialogNewVolumeBinding
import com.purewrite.writer.databinding.ItemChapterBinding
import com.purewrite.writer.databinding.ItemVolumeBinding
import com.purewrite.writer.ui.editor.EditorActivity
import com.purewrite.writer.ui.reader.BookReaderActivity
import com.purewrite.writer.ui.sensitive.SensitiveWordActivity
import com.purewrite.writer.util.ExportUtils
import com.purewrite.writer.util.TimeUtils
import com.purewrite.writer.util.WordCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书籍结构页：展示卷→章的三级层次
 * 卷可伸缩展开/收起，卷下显示章节列表
 */
class ChapterListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChaptersBinding
    private lateinit var adapter: BookStructureAdapter
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
        loadStructure()
    }

    private fun setupToolbar() {
        binding.toolbar.title = bookTitle
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_chapters)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reader -> openReader()
                R.id.action_export_txt -> exportBook(asMarkdown = false)
                R.id.action_export_md -> exportBook(asMarkdown = true)
                R.id.action_sensitive_check -> checkWholeBookSensitive()
            }
            true
        }
    }

    private fun setupRecyclerView() {
        adapter = BookStructureAdapter(
            onVolumeClick = { volume -> toggleVolumeExpand(volume) },
            onVolumeMore = { volume, view -> showVolumeMenu(volume, view) },
            onAddChapterInVolume = { volume -> showNewChapterDialog(volume) },
            onChapterClick = { chapter -> openChapter(chapter) },
            onChapterMore = { chapter, view -> showChapterMenu(chapter, view) }
        )
        binding.rvChapters.layoutManager = LinearLayoutManager(this)
        binding.rvChapters.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddChapter.setOnClickListener { showNewVolumeDialog() }
    }

    private fun loadBookInfo() {
        binding.toolbar.title = bookTitle
    }

    // ==================== 数据加载 ====================

    private val expandedVolumes = mutableSetOf<Long>()

    private fun loadStructure() {
        lifecycleScope.launch {
            repository.getVolumesByBook(bookId).collectLatest { volumes ->
                if (volumes.isEmpty()) {
                    adapter.submitList(emptyList())
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    // 默认展开第一个卷
                    if (expandedVolumes.isEmpty() && volumes.isNotEmpty()) {
                        expandedVolumes.add(volumes.first().id)
                    }
                    buildStructure(volumes)
                }
            }
        }
    }

    private fun buildStructure(volumes: List<VolumeEntity>) {
        lifecycleScope.launch {
            val items = mutableListOf<BookStructureItem>()
            volumes.forEach { volume ->
                val isExpanded = expandedVolumes.contains(volume.id)
                val chapterCount = repository.getChapterCountByVolume(volume.id)
                items.add(BookStructureItem.VolumeItem(volume, chapterCount, isExpanded))
                if (isExpanded) {
                    val chapters = repository.getChaptersByVolumeList(volume.id)
                    chapters.forEach { ch ->
                        items.add(BookStructureItem.ChapterItem(ch, volume.title))
                    }
                }
            }
            withContext(Dispatchers.Main) {
                adapter.submitList(items)
            }
        }
    }

    private fun toggleVolumeExpand(volume: VolumeEntity) {
        if (expandedVolumes.contains(volume.id)) {
            expandedVolumes.remove(volume.id)
        } else {
            expandedVolumes.add(volume.id)
        }
        lifecycleScope.launch {
            val volumes = repository.getVolumesByBookList(bookId)
            buildStructure(volumes)
        }
    }

    // ==================== 卷 CRUD ====================

    private fun showNewVolumeDialog() {
        val dialogBinding = DialogNewVolumeBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_volume)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.etVolumeTitle.text.toString().trim()
                if (title.isNotEmpty()) createVolume(title)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createVolume(title: String) {
        lifecycleScope.launch {
            val order = repository.getVolumeCount(bookId)
            val volume = VolumeEntity(bookId = bookId, title = title, order = order)
            val id = repository.insertVolume(volume)
            expandedVolumes.add(id)
            // 重新加载
            val volumes = repository.getVolumesByBookList(bookId)
            buildStructure(volumes)
        }
    }

    private fun showVolumeMenu(volume: VolumeEntity, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_volume_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_volume_rename -> showRenameVolumeDialog(volume)
                R.id.action_volume_delete -> showDeleteVolumeConfirm(volume)
            }
            true
        }
        popup.show()
    }

    private fun showRenameVolumeDialog(volume: VolumeEntity) {
        val dialogBinding = DialogNewVolumeBinding.inflate(layoutInflater)
        dialogBinding.etVolumeTitle.setText(volume.title)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.etVolumeTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.updateVolume(volume.copy(title = title, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteVolumeConfirm(volume: VolumeEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage("删除后卷及其章节将移入回收站，可恢复。是否确认？")
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.softDeleteVolume(volume.id)
                    expandedVolumes.remove(volume.id)
                    Toast.makeText(this@ChapterListActivity, "已移入回收站", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== 章节 CRUD ====================

    private fun showNewChapterDialog(volume: VolumeEntity) {
        val dialogBinding = DialogNewChapterBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_chapter)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.etChapterTitle.text.toString().trim()
                if (title.isNotEmpty()) createChapter(volume, title)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createChapter(volume: VolumeEntity, title: String) {
        lifecycleScope.launch {
            val order = repository.getChapterCountByVolume(volume.id)
            val chapter = ChapterEntity(volumeId = volume.id, title = title, order = order)
            val id = repository.insertChapter(chapter)
            expandedVolumes.add(volume.id)
            val intent = Intent(this@ChapterListActivity, EditorActivity::class.java).apply {
                putExtra("chapter_id", id)
                putExtra("book_id", bookId)
            }
            startActivity(intent)
        }
    }

    private fun openChapter(chapter: ChapterEntity) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("chapter_id", chapter.id)
            putExtra("book_id", bookId)
        }
        startActivity(intent)
    }

    private fun showChapterMenu(chapter: ChapterEntity, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_chapter_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> showRenameChapterDialog(chapter)
                R.id.action_export_txt -> exportChapter(chapter, asMarkdown = false)
                R.id.action_export_md -> exportChapter(chapter, asMarkdown = true)
                R.id.action_sensitive_check -> checkChapterSensitive(chapter)
                R.id.action_delete -> showDeleteChapterConfirm(chapter)
            }
            true
        }
        popup.show()
    }

    private fun showRenameChapterDialog(chapter: ChapterEntity) {
        val dialogBinding = DialogNewChapterBinding.inflate(layoutInflater)
        dialogBinding.etChapterTitle.setText(chapter.title)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
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

    private fun showDeleteChapterConfirm(chapter: ChapterEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage("删除后章节将移入回收站，可恢复。是否确认？")
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.softDeleteChapter(chapter.id)
                    Toast.makeText(this@ChapterListActivity, "已移入回收站", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== 阅读模式 ====================

    private fun openReader() {
        val intent = Intent(this, BookReaderActivity::class.java).apply {
            putExtra("book_id", bookId)
            putExtra("book_title", bookTitle)
        }
        startActivity(intent)
    }

    /** 单章敏感词检测 */
    private fun checkChapterSensitive(chapter: ChapterEntity) {
        startActivity(
            SensitiveWordActivity.createIntent(
                this,
                chapter.title.ifEmpty { getString(R.string.untitled_chapter) },
                chapter.content
            )
        )
    }

    /** 全书敏感词检测：拼接所有章节内容 */
    private fun checkWholeBookSensitive() {
        lifecycleScope.launch {
            val chapters = repository.getChaptersByBook(bookId)
            if (chapters.isEmpty()) {
                Toast.makeText(this@ChapterListActivity, "暂无章节", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sb = StringBuilder()
            chapters.forEachIndexed { index, ch ->
                sb.append("【第").append(index + 1).append("章 ").append(ch.title).append("】\n")
                sb.append(ch.content).append("\n\n")
            }
            startActivity(
                SensitiveWordActivity.createIntent(
                    this@ChapterListActivity,
                    "$bookTitle（全书）",
                    sb.toString()
                )
            )
        }
    }

    // ==================== 导出 ====================

    private fun exportBook(asMarkdown: Boolean) {
        val ext = if (asMarkdown) "md" else "txt"
        val mime = if (asMarkdown) "text/markdown" else "text/plain"
        currentExportBookIsMd = asMarkdown
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, "$bookTitle.$ext")
        }
        startActivityForResult(intent, REQUEST_EXPORT_BOOK)
    }

    private fun exportChapter(chapter: ChapterEntity, asMarkdown: Boolean) {
        val ext = if (asMarkdown) "md" else "txt"
        val mime = if (asMarkdown) "text/markdown" else "text/plain"
        currentExportChapter = chapter
        currentExportChapterIsMd = asMarkdown
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, "${chapter.title}.$ext")
        }
        startActivityForResult(intent, REQUEST_EXPORT_CHAPTER)
    }

    private var currentExportChapter: ChapterEntity? = null
    private var currentExportChapterIsMd = false
    private var currentExportBookIsMd = false

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQUEST_EXPORT_BOOK -> {
                val asMd = currentExportBookIsMd
                lifecycleScope.launch {
                    val book = repository.getBookById(bookId) ?: return@launch
                    val chapters = repository.getChaptersByBook(bookId)
                    val success = if (asMd) {
                        ExportUtils.exportBookToMarkdown(this@ChapterListActivity, uri, book, chapters)
                    } else {
                        ExportUtils.exportBookToTxt(this@ChapterListActivity, uri, book, chapters)
                    }
                    withContext(Dispatchers.Main) {
                        showToast(if (success) R.string.export_success else R.string.export_failed)
                    }
                }
            }
            REQUEST_EXPORT_CHAPTER -> {
                currentExportChapter?.let { chapter ->
                    val asMd = currentExportChapterIsMd
                    val success = if (asMd) {
                        ExportUtils.exportChapterToMarkdown(this, uri, chapter)
                    } else {
                        ExportUtils.exportChapterToTxt(this, uri, chapter)
                    }
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

// ==================== 数据模型 ====================

sealed class BookStructureItem {
    data class VolumeItem(
        val volume: VolumeEntity,
        val chapterCount: Int,
        val isExpanded: Boolean
    ) : BookStructureItem()

    data class ChapterItem(
        val chapter: ChapterEntity,
        val volumeTitle: String
    ) : BookStructureItem()
}

// ==================== 适配器 ====================

class BookStructureAdapter(
    private val onVolumeClick: (VolumeEntity) -> Unit,
    private val onVolumeMore: (VolumeEntity, View) -> Unit,
    private val onAddChapterInVolume: (VolumeEntity) -> Unit,
    private val onChapterClick: (ChapterEntity) -> Unit,
    private val onChapterMore: (ChapterEntity, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<BookStructureItem>()

    private companion object {
        const val TYPE_VOLUME = 0
        const val TYPE_CHAPTER = 1
    }

    fun submitList(list: List<BookStructureItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BookStructureItem.VolumeItem -> TYPE_VOLUME
            is BookStructureItem.ChapterItem -> TYPE_CHAPTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_VOLUME -> VolumeViewHolder(ItemVolumeBinding.inflate(inflater, parent, false))
            else -> ChapterViewHolder(ItemChapterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BookStructureItem.VolumeItem -> {
                val vh = holder as VolumeViewHolder
                vh.bind(item)
            }
            is BookStructureItem.ChapterItem -> {
                val vh = holder as ChapterViewHolder
                vh.bind(item, position)
            }
        }
    }

    override fun getItemCount() = items.size

    inner class VolumeViewHolder(val binding: ItemVolumeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookStructureItem.VolumeItem) {
            val vol = item.volume
            binding.tvVolumeTitle.text = vol.title
            binding.tvVolumeChapterCount.text = "(${item.chapterCount}${binding.root.context.getString(R.string.chapter_count)})"
            // 伸缩图标
            binding.ivExpandIcon.rotation = if (item.isExpanded) 90f else 0f
            // 章节容器和添加按钮
            binding.layoutChaptersContainer.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            binding.btnAddChapterInVolume.visibility = if (item.isExpanded) View.VISIBLE else View.GONE

            binding.layoutVolumeHeader.setOnClickListener { onVolumeClick(vol) }
            binding.btnVolumeMore.setOnClickListener { onVolumeMore(vol, it) }
            binding.btnAddChapterInVolume.setOnClickListener { onAddChapterInVolume(vol) }
        }
    }

    inner class ChapterViewHolder(val binding: ItemChapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookStructureItem.ChapterItem, position: Int) {
            val chapter = item.chapter
            binding.tvChapterIndex.text = (position).toString()
            binding.tvChapterTitle.text = chapter.title
            binding.tvChapterPreview.text = chapter.content.take(50).ifBlank { "（空章节）" }
            binding.tvChapterWords.text = "${binding.root.context.getString(R.string.word_count)} ${chapter.wordCount}"
            binding.tvChapterUpdated.text = TimeUtils.getFriendlyTime(chapter.updatedAt)

            binding.root.setOnClickListener { onChapterClick(chapter) }
            binding.root.setOnLongClickListener {
                onChapterMore(chapter, it)
                true
            }
        }
    }
}
