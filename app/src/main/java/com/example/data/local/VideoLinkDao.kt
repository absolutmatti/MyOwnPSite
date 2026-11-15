package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoLinkDao {
    @Query("SELECT * FROM video_links WHERE performerId = :performerId ORDER BY addedAt DESC")
    fun observeLinksForPerformer(performerId: Long): Flow<List<VideoLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: VideoLinkEntity): Long

    @Query("UPDATE video_links SET title = :title, url = :url WHERE id = :id")
    suspend fun update(id: Long, title: String, url: String)

    @Query("DELETE FROM video_links WHERE id = :id")
    suspend fun deleteById(id: Long)
}
