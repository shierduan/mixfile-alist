package com.purewrite.writer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE deletedAt = 0 ORDER BY lastOpenedAt DESC, updatedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE deletedAt = 0 ORDER BY lastOpenedAt DESC, updatedAt DESC")
    suspend fun getAllBooksList(): List<BookEntity>

    @Query("SELECT * FROM books WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    suspend fun getDeletedBooks(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("UPDATE books SET deletedAt = :time WHERE id = :id")
    suspend fun softDeleteBook(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE books SET deletedAt = 0 WHERE id = :id")
    suspend fun restoreBook(id: Long)

    @Query("UPDATE books SET lastOpenedAt = :time WHERE id = :id")
    suspend fun updateLastOpened(id: Long, time: Long)

    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()
}
