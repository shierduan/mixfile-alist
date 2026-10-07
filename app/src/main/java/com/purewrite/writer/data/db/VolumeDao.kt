package com.purewrite.writer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VolumeDao {

    @Query("SELECT * FROM volumes WHERE bookId = :bookId AND deletedAt = 0 ORDER BY `order` ASC, createdAt ASC")
    fun getVolumesByBook(bookId: Long): Flow<List<VolumeEntity>>

    @Query("SELECT * FROM volumes WHERE bookId = :bookId AND deletedAt = 0 ORDER BY `order` ASC, createdAt ASC")
    suspend fun getVolumesByBookList(bookId: Long): List<VolumeEntity>

    @Query("SELECT * FROM volumes WHERE id = :id")
    suspend fun getVolumeById(id: Long): VolumeEntity?

    @Query("SELECT * FROM volumes WHERE deletedAt = 0 ORDER BY `order` ASC, createdAt ASC")
    suspend fun getAllVolumesList(): List<VolumeEntity>

    @Query("SELECT * FROM volumes WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    suspend fun getDeletedVolumes(): List<VolumeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVolume(volume: VolumeEntity): Long

    @Update
    suspend fun updateVolume(volume: VolumeEntity)

    @Delete
    suspend fun deleteVolume(volume: VolumeEntity)

    @Query("DELETE FROM volumes WHERE id = :id")
    suspend fun deleteVolumeById(id: Long)

    @Query("UPDATE volumes SET deletedAt = :time WHERE id = :id")
    suspend fun softDeleteVolume(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE volumes SET deletedAt = 0 WHERE id = :id")
    suspend fun restoreVolume(id: Long)

    @Query("SELECT COUNT(*) FROM volumes WHERE bookId = :bookId AND deletedAt = 0")
    suspend fun getVolumeCount(bookId: Long): Int

    @Query("DELETE FROM volumes")
    suspend fun deleteAllVolumes()
}
