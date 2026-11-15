package com.example.data.local

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
) {
    fun toDomain(): Performer = Performer(
        id = id,
        name = name,
        imageUri = imageUri,
        isFavorite = isFavorite,
        createdAt = createdAt,
    )
}
