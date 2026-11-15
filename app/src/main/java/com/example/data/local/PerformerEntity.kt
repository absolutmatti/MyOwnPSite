package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Performer

@Entity(tableName = "performers")
data class PerformerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val imageUri: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class PerformerWithVideoCount(
    @Embedded val performer: PerformerEntity,
    val videoCount: Int,
) {
    fun toDomain(): Performer = Performer(
        id = performer.id,
        name = performer.name,
        imageUri = performer.imageUri,
        videoCount = videoCount,
        createdAt = performer.createdAt,
    )
}
