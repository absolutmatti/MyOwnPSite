package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformerDao {
    @Query("SELECT * FROM performers ORDER BY name COLLATE NOCASE ASC")
    fun observeAllPerformers(): Flow<List<PerformerEntity>>

    @Query("SELECT * FROM performers WHERE id = :id LIMIT 1")
    fun observePerformerById(id: Long): Flow<PerformerEntity?>

    @Query("SELECT COUNT(*) FROM performers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PerformerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PerformerEntity): Long

    @Query("UPDATE performers SET imageUri = :imageUri WHERE id = :id")
    suspend fun updateImageUri(id: Long, imageUri: String?)

    @Query("UPDATE performers SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE performers SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("DELETE FROM performers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
