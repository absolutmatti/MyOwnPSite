package com.example.domain.model

data class VideoLink(
    val id: String = "",
    val performerId: String = "",
    val title: String = "",
    val url: String = "",
    val thumbnailUrl: String? = null,
    val addedAt: Long = 0L,
) {
    val hasThumbnail: Boolean
        get() = !thumbnailUrl.isNullOrBlank()
}
