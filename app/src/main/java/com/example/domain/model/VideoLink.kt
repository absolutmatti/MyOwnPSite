package com.example.domain.model

data class VideoLink(
    val id: Long,
    val performerId: Long,
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val addedAt: Long,
) {
    val hasThumbnail: Boolean
        get() = !thumbnailUrl.isNullOrBlank()
}
