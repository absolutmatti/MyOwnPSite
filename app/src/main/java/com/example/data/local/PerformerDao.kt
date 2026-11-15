package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformerDao {
    @Query(
        """
        SELECT p.*, COUNT(v.id) AS videoCount
        FROM performers p
        LEFT JOIN video_links v ON p.id = v.performerId
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observeAllPerformersWithVideoCount(): Flow<List<PerformerWithVideoCount>>

    @Query("SELECT COUNT(*) FROM performers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PerformerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PerformerEntity): Long

    @Query("UPDATE performers SET imageUri = :imageUri WHERE id = :id")
    suspend fun updateImageUri(id: Long, imageUri: String?)

    @Query("UPDATE performers SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("DELETE FROM performers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
