package com.example.domain.model

data class Performer(
    val id: String = "",
    val name: String = "",
    val imageUri: String? = null,
    val videoCount: Int = 0,
    val createdAt: Long = 0L,
) {
    val hasImage: Boolean
        get() = !imageUri.isNullOrBlank()

    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts.first().take(2).uppercase()
                else -> "${parts.first().first()}${parts.last().first()}".uppercase()
            }
        }
}
