package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.VideoLink

@Entity(
    tableName = "video_links",
    foreignKeys = [
        ForeignKey(
            entity = PerformerEntity::class,
            parentColumns = ["id"],
            childColumns = ["performerId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["performerId"])],
)
data class VideoLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val performerId: Long,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): VideoLink = VideoLink(
        id = id,
        performerId = performerId,
        title = title,
        url = url,
        thumbnailUrl = thumbnailUrl,
        addedAt = addedAt,
    )
}
