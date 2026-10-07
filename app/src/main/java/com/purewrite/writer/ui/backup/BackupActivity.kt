package com.purewrite.writer.ui.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import com.purewrite.writer.data.db.VolumeEntity
import com.purewrite.writer.databinding.ActivityBackupBinding
import com.purewrite.writer.databinding.ItemBackupBinding
import com.purewrite.writer.util.AutoBackupManager
import com.purewrite.writer.util.BackupManager
import com.purewrite.writer.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份与恢复页面
 *
 * 支持：
 * - 立即备份（内部存储）
 * - 导出备份到用户指定位置（本地/云盘，通过 SAF）
 * - 从备份文件恢复（覆盖/合并模式）
 * - 自动备份开关
 * - 内部备份历史列表（恢复/删除）
 */
class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private lateinit var prefs: PrefsManager
    private val repository get() = (application as App).repository

    private val backupAdapter = BackupHistoryAdapter(
        onRestore = { record -> showRestoreDialog(record) },
        onDelete = { record -> deleteBackup(record) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvBackupHistory.layoutManager = LinearLayoutManager(this)
        binding.rvBackupHistory.adapter = backupAdapter

        // 自动备份开关
        binding.switchAutoBackup.isChecked = prefs.autoBackup
        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoBackup = isChecked
            Toast.makeText(this, if (isChecked) "已开启自动备份" else "已关闭自动备份", Toast.LENGTH_SHORT).show()
        }

        // 立即备份
        binding.btnBackupNow.setOnClickListener {
            performInternalBackup()
        }

        // 导出到云盘
        binding.btnExportBackup.setOnClickListener {
            exportBackupToCloud()
        }

        // 从文件恢复
        binding.btnRestore.setOnClickListener {
            pickBackupFileForRestore()
        }

        loadBackupHistory()
    }

    /** 立即创建内部备份 */
    private fun performInternalBackup() {
        lifecycleScope.launch {
            val record = AutoBackupManager.createInternalBackup(this@BackupActivity)
            if (record != null) {
                Toast.makeText(this@BackupActivity, "备份成功", Toast.LENGTH_SHORT).show()
                loadBackupHistory()
            } else {
                Toast.makeText(this@BackupActivity, "备份失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 导出备份到用户选定的位置 */
    private fun exportBackupToCloud() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, BackupManager.generateBackupFileName())
        }
        startActivityForResult(intent, REQUEST_CREATE_DOCUMENT)
    }

    /** 选择备份文件用于恢复 */
    private fun pickBackupFileForRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data ?: return

        when (requestCode) {
            REQUEST_CREATE_DOCUMENT -> writeBackupToUri(uri)
            REQUEST_OPEN_DOCUMENT -> readBackupFromUri(uri)
        }
    }

    /** 将当前数据写入用户选定的 URI */
    private fun writeBackupToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val books = repository.getAllBooksList()
                val volumes = repository.getAllVolumesList()
                val chapters = repository.getAllChaptersList()
                val backupData = BackupManager.BackupData(
                    books = books,
                    volumes = volumes,
                    chapters = chapters
                )
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        BackupManager.writeBackup(backupData, out)
                    }
                }
                Toast.makeText(this@BackupActivity, "已导出到所选位置", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@BackupActivity, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 从用户选定的 URI 读取备份数据 */
    private fun readBackupFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backupData = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        BackupManager.readBackup(input)
                    }
                }
                if (backupData == null) {
                    Toast.makeText(this@BackupActivity, "无效的备份文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                showRestoreModeDialog(backupData)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@BackupActivity, "读取失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 显示恢复模式选择对话框 */
    private fun showRestoreModeDialog(data: BackupManager.BackupData) {
        val info = "书籍 ${data.books.size} 本 · 卷 ${data.volumes.size} 个 · 章节 ${data.chapters.size} 章"
        MaterialAlertDialogBuilder(this)
            .setTitle("恢复备份")
            .setMessage("备份内容：$info\n\n请选择恢复方式：")
            .setPositiveButton("覆盖恢复（清空现有数据）") { _, _ ->
                restoreBackup(data, merge = false)
            }
            .setNegativeButton("合并恢复（保留现有数据）") { _, _ ->
                restoreBackup(data, merge = true)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    /** 执行恢复 */
    private fun restoreBackup(data: BackupManager.BackupData, merge: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!merge) {
                        repository.clearAll()
                    }
                    // 重建 ID 映射，避免主键冲突
                    val bookIdMap = mutableMapOf<Long, Long>()
                    val volumeIdMap = mutableMapOf<Long, Long>()

                    for (book in data.books) {
                        val newId = repository.insertBook(
                            book.copy(id = 0, createdAt = book.createdAt, updatedAt = book.updatedAt)
                        )
                        bookIdMap[book.id] = newId
                    }
                    for (volume in data.volumes) {
                        val newBookId = bookIdMap[volume.bookId] ?: continue
                        val newVolumeId = repository.insertVolume(
                            volume.copy(id = 0, bookId = newBookId)
                        )
                        volumeIdMap[volume.id] = newVolumeId
                    }
                    for (chapter in data.chapters) {
                        val newVolumeId = volumeIdMap[chapter.volumeId] ?: continue
                        repository.insertChapter(
                            chapter.copy(id = 0, volumeId = newVolumeId)
                        )
                    }
                }
                Toast.makeText(this@BackupActivity, "恢复成功", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@BackupActivity, "恢复失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 从内部备份历史恢复 */
    private fun showRestoreDialog(record: AutoBackupManager.BackupRecord) {
        val data = AutoBackupManager.getBackupData(this, record.fileName)
        if (data == null) {
            Toast.makeText(this, "备份文件已损坏", Toast.LENGTH_SHORT).show()
            loadBackupHistory()
            return
        }
        showRestoreModeDialog(data)
    }

    /** 删除内部备份 */
    private fun deleteBackup(record: AutoBackupManager.BackupRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除备份")
            .setMessage("确定删除此备份吗？")
            .setPositiveButton("删除") { _, _ ->
                AutoBackupManager.deleteBackup(this, record.fileName)
                loadBackupHistory()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 加载备份历史列表 */
    private fun loadBackupHistory() {
        val backups = AutoBackupManager.listBackups(this)
        backupAdapter.submitList(backups)
        binding.tvNoBackup.visibility = if (backups.isEmpty()) View.VISIBLE else View.GONE
        binding.rvBackupHistory.visibility = if (backups.isEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        private const val REQUEST_CREATE_DOCUMENT = 1001
        private const val REQUEST_OPEN_DOCUMENT = 1002
    }

    // ============ 备份历史适配器 ============
    inner class BackupHistoryAdapter(
        private val onRestore: (AutoBackupManager.BackupRecord) -> Unit,
        private val onDelete: (AutoBackupManager.BackupRecord) -> Unit
    ) : RecyclerView.Adapter<BackupHistoryAdapter.ViewHolder>() {

        private val items = mutableListOf<AutoBackupManager.BackupRecord>()

        fun submitList(list: List<AutoBackupManager.BackupRecord>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemBackupBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBackupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvBackupTime.text = AutoBackupManager.formatTime(item.createdAt)
            val info = "${item.bookCount} 本书 · ${item.chapterCount} 章 · ${AutoBackupManager.formatSize(item.sizeBytes)}"
            holder.binding.tvBackupInfo.text = info
            holder.binding.btnRestore.setOnClickListener { onRestore(item) }
            holder.binding.btnDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
