package com.example.data.repository

import com.example.data.local.InitialPerformers
import com.example.data.local.PerformerDao
import com.example.data.local.PerformerEntity
import com.example.data.local.VideoLinkDao
import com.example.data.local.VideoLinkEntity
import com.example.domain.model.Performer
import com.example.domain.model.VideoLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PerformerRepository(
    private val performerDao: PerformerDao,
    private val videoLinkDao: VideoLinkDao,
) {
    fun observeAllPerformers(): Flow<List<Performer>> =
        performerDao.observeAllPerformersWithVideoCount().map { list ->
            list.map { it.toDomain() }
        }

    fun observeVideoLinks(performerId: Long): Flow<List<VideoLink>> =
        videoLinkDao.observeLinksForPerformer(performerId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun ensureSeeded() {
        if (performerDao.count() == 0) {
            val baseTime = System.currentTimeMillis()
            val seedEntities = InitialPerformers.names.mapIndexed { index, name ->
                PerformerEntity(
                    name = name.trim(),
                    imageUri = null,
                    isFavorite = false,
                    createdAt = baseTime + index,
                )
            }
            performerDao.insertAll(seedEntities)
        }
    }

    suspend fun addPerformer(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Name darf nicht leer sein" }
        return performerDao.insert(
            PerformerEntity(
                name = trimmed,
                imageUri = null,
                isFavorite = false,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateImageUri(id: Long, imageUri: String?) {
        val normalized = imageUri?.trim()?.takeIf { it.isNotEmpty() }
        performerDao.updateImageUri(id = id, imageUri = normalized)
    }

    suspend fun renamePerformer(id: Long, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Name darf nicht leer sein" }
        performerDao.updateName(id = id, name = trimmed)
    }

    suspend fun deletePerformer(id: Long) {
        performerDao.deleteById(id)
    }

    suspend fun addVideoLink(performerId: Long, title: String, url: String): Long {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotEmpty()) { "Video-Link darf nicht leer sein" }
        val resolvedTitle = title.trim().ifEmpty { deriveTitleFromUrl(normalizedUrl) }
        return videoLinkDao.insert(
            VideoLinkEntity(
                performerId = performerId,
                title = resolvedTitle,
                url = normalizedUrl,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateVideoLink(linkId: Long, title: String, url: String) {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotEmpty()) { "Video-Link darf nicht leer sein" }
        val resolvedTitle = title.trim().ifEmpty { deriveTitleFromUrl(normalizedUrl) }
        videoLinkDao.update(id = linkId, title = resolvedTitle, url = normalizedUrl)
    }

    suspend fun deleteVideoLink(linkId: Long) {
        videoLinkDao.deleteById(linkId)
    }

    private fun deriveTitleFromUrl(url: String): String {
        val withoutQuery = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val lastSegment = withoutQuery.substringAfterLast('/', "").trim()
        return lastSegment.ifEmpty {
            url.removePrefix("https://").removePrefix("http://").take(36)
        }
    }
}
