package com.purewrite.writer.data.repository

import com.purewrite.writer.data.db.BookDao
import com.purewrite.writer.data.db.BookEntity
import com.purewrite.writer.data.db.ChapterDao
import com.purewrite.writer.data.db.ChapterEntity
import kotlinx.coroutines.flow.Flow

class WritingRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao
) {
    // Book operations
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)

    suspend fun deleteBookById(id: Long) = bookDao.deleteBookById(id)

    suspend fun updateLastOpened(id: Long, time: Long = System.currentTimeMillis()) =
        bookDao.updateLastOpened(id, time)

    // Chapter operations
    fun getChaptersByBook(bookId: Long): Flow<List<ChapterEntity>> =
        chapterDao.getChaptersByBook(bookId)

    suspend fun getChapterById(id: Long): ChapterEntity? = chapterDao.getChapterById(id)

    suspend fun insertChapter(chapter: ChapterEntity): Long = chapterDao.insertChapter(chapter)

    suspend fun updateChapter(chapter: ChapterEntity) = chapterDao.updateChapter(chapter)

    suspend fun deleteChapter(chapter: ChapterEntity) = chapterDao.deleteChapter(chapter)

    suspend fun deleteChapterById(id: Long) = chapterDao.deleteChapterById(id)

    suspend fun getChapterCount(bookId: Long): Int = chapterDao.getChapterCount(bookId)

    suspend fun getTotalWords(bookId: Long): Int = chapterDao.getTotalWords(bookId)

    suspend fun getChapterAt(bookId: Long, offset: Int): ChapterEntity? =
        chapterDao.getChapterAt(bookId, offset)
}
