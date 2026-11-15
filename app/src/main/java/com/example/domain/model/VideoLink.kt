package com.example.domain.model

data class VideoLink(
    val id: Long,
    val performerId: Long,
    val title: String,
    val url: String,
    val addedAt: Long,
)
