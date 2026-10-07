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

    @Query("SELECT * FROM volumes WHERE bookId = :bookId ORDER BY `order` ASC, createdAt ASC")
    fun getVolumesByBook(bookId: Long): Flow<List<VolumeEntity>>

    @Query("SELECT * FROM volumes WHERE bookId = :bookId ORDER BY `order` ASC, createdAt ASC")
    suspend fun getVolumesByBookList(bookId: Long): List<VolumeEntity>

    @Query("SELECT * FROM volumes WHERE id = :id")
    suspend fun getVolumeById(id: Long): VolumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVolume(volume: VolumeEntity): Long

    @Update
    suspend fun updateVolume(volume: VolumeEntity)

    @Delete
    suspend fun deleteVolume(volume: VolumeEntity)

    @Query("DELETE FROM volumes WHERE id = :id")
    suspend fun deleteVolumeById(id: Long)

    @Query("SELECT COUNT(*) FROM volumes WHERE bookId = :bookId")
    suspend fun getVolumeCount(bookId: Long): Int
}
