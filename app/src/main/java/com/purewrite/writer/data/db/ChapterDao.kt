package com.purewrite.writer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE volumeId = :volumeId ORDER BY `order` ASC, createdAt ASC")
    fun getChaptersByVolume(volumeId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE volumeId = :volumeId ORDER BY `order` ASC, createdAt ASC")
    suspend fun getChaptersByVolumeList(volumeId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapterById(id: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE volumeId = :volumeId")
    suspend fun getChapterCountByVolume(volumeId: Long): Int

    /**
     * 获取书籍全部章节（通过 JOIN 卷表），按卷顺序+章节顺序排列
     */
    @Query(
        """SELECT c.* FROM chapters c
           INNER JOIN volumes v ON c.volumeId = v.id
           WHERE v.bookId = :bookId
           ORDER BY v.`order` ASC, v.createdAt ASC, c.`order` ASC, c.createdAt ASC"""
    )
    suspend fun getChaptersByBook(bookId: Long): List<ChapterEntity>

    @Query(
        """SELECT COUNT(*) FROM chapters c
           INNER JOIN volumes v ON c.volumeId = v.id
           WHERE v.bookId = :bookId"""
    )
    suspend fun getChapterCountByBook(bookId: Long): Int

    @Query(
        """SELECT COALESCE(SUM(c.wordCount), 0) FROM chapters c
           INNER JOIN volumes v ON c.volumeId = v.id
           WHERE v.bookId = :bookId"""
    )
    suspend fun getTotalWordsByBook(bookId: Long): Int
}
