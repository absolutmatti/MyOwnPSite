package com.example.data.repository

import com.example.data.local.InitialPerformers
import com.example.data.local.PerformerDao
import com.example.data.local.PerformerEntity
import com.example.domain.model.Performer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PerformerRepository(
    private val performerDao: PerformerDao,
) {
    fun observeAllPerformers(): Flow<List<Performer>> =
        performerDao.observeAllPerformers().map { entities ->
            entities.map { it.toDomain() }
        }

    fun observePerformerById(id: Long): Flow<Performer?> =
        performerDao.observePerformerById(id).map { it?.toDomain() }

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

    suspend fun toggleFavorite(id: Long, currentFavorite: Boolean) {
        performerDao.updateFavorite(id = id, isFavorite = !currentFavorite)
    }

    suspend fun renamePerformer(id: Long, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Name darf nicht leer sein" }
        performerDao.updateName(id = id, name = trimmed)
    }

    suspend fun deletePerformer(id: Long) {
        performerDao.deleteById(id)
    }
}
