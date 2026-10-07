package com.purewrite.writer.data.repository

import com.purewrite.writer.data.db.BookDao
import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.data.db.ChapterDao
import com.purewrite.writer.data.db.ChapterEntity
import com.purewrite.writer.data.db.VolumeDao
import com.purewrite.writer.data.db.VolumeEntity
import kotlinx.coroutines.flow.Flow

class WritingRepository(
    private val bookDao: BookDao,
    private val volumeDao: VolumeDao,
    private val chapterDao: ChapterDao
) {
    // ==================== Book ====================
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)

    suspend fun deleteBookById(id: Long) = bookDao.deleteBookById(id)

    suspend fun updateLastOpened(id: Long, time: Long = System.currentTimeMillis()) =
        bookDao.updateLastOpened(id, time)

    // ==================== Volume ====================
    fun getVolumesByBook(bookId: Long): Flow<List<VolumeEntity>> = volumeDao.getVolumesByBook(bookId)

    suspend fun getVolumesByBookList(bookId: Long): List<VolumeEntity> = volumeDao.getVolumesByBookList(bookId)

    suspend fun getVolumeById(id: Long): VolumeEntity? = volumeDao.getVolumeById(id)

    suspend fun insertVolume(volume: VolumeEntity): Long = volumeDao.insertVolume(volume)

    suspend fun updateVolume(volume: VolumeEntity) = volumeDao.updateVolume(volume)

    suspend fun deleteVolume(volume: VolumeEntity) = volumeDao.deleteVolume(volume)

    suspend fun deleteVolumeById(id: Long) = volumeDao.deleteVolumeById(id)

    suspend fun getVolumeCount(bookId: Long): Int = volumeDao.getVolumeCount(bookId)

    // ==================== Chapter ====================
    fun getChaptersByVolume(volumeId: Long): Flow<List<ChapterEntity>> = chapterDao.getChaptersByVolume(volumeId)

    suspend fun getChaptersByVolumeList(volumeId: Long): List<ChapterEntity> = chapterDao.getChaptersByVolumeList(volumeId)

    suspend fun getChapterById(id: Long): ChapterEntity? = chapterDao.getChapterById(id)

    suspend fun insertChapter(chapter: ChapterEntity): Long = chapterDao.insertChapter(chapter)

    suspend fun updateChapter(chapter: ChapterEntity) = chapterDao.updateChapter(chapter)

    suspend fun deleteChapter(chapter: ChapterEntity) = chapterDao.deleteChapter(chapter)

    suspend fun deleteChapterById(id: Long) = chapterDao.deleteChapterById(id)

    suspend fun getChapterCountByVolume(volumeId: Long): Int = chapterDao.getChapterCountByVolume(volumeId)

    /**
     * 获取书籍全部章节（按卷顺序+章节顺序）
     */
    suspend fun getChaptersByBook(bookId: Long): List<ChapterEntity> = chapterDao.getChaptersByBook(bookId)

    suspend fun getChapterCountByBook(bookId: Long): Int = chapterDao.getChapterCountByBook(bookId)

    suspend fun getTotalWordsByBook(bookId: Long): Int = chapterDao.getTotalWordsByBook(bookId)
}
