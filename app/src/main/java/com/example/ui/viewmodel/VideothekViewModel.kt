package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PerformerRepository
import com.example.domain.model.AlphabetBucket
import com.example.domain.model.Performer
import com.example.domain.model.SortOrder
import com.example.domain.model.VideoLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CatalogContentState {
    data object Loading : CatalogContentState
    data class Ready(
        val performers: List<Performer>,
        val totalCount: Int,
        val totalVideoLinks: Int,
    ) : CatalogContentState
    data class Error(val message: String) : CatalogContentState
}

sealed interface ScreenDestination {
    data object Catalog : ScreenDestination
    data class Detail(val performerId: Long) : ScreenDestination
}

data class VideothekUiState(
    val contentState: CatalogContentState = CatalogContentState.Loading,
    val destination: ScreenDestination = ScreenDestination.Catalog,
    val selectedPerformer: Performer? = null,
    val selectedPerformerVideoLinks: List<VideoLink> = emptyList(),
    val lastFocusedPerformerId: Long? = null,
    val sortOrder: SortOrder = SortOrder.ALPHA_ASC,
    val alphabetBucket: AlphabetBucket = AlphabetBucket.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val showAddPerformerDialog: Boolean = false,
    val statusMessage: String? = null,
)

private data class FilterCriteria(
    val sortOrder: SortOrder,
    val alphabetBucket: AlphabetBucket,
    val searchQuery: String,
    val isSearchActive: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class VideothekViewModel(
    private val repository: PerformerRepository,
) : ViewModel() {

    private val destinationState = MutableStateFlow<ScreenDestination>(ScreenDestination.Catalog)
    private val lastFocusedIdState = MutableStateFlow<Long?>(null)
    private val filterCriteriaState = MutableStateFlow(
        FilterCriteria(
            sortOrder = SortOrder.ALPHA_ASC,
            alphabetBucket = AlphabetBucket.ALL,
            searchQuery = "",
            isSearchActive = false,
        )
    )
    private val showAddDialogState = MutableStateFlow(false)
    private val statusMessageState = MutableStateFlow<String?>(null)
    private val seedErrorState = MutableStateFlow<String?>(null)

    private val selectedVideoLinksFlow = destinationState.flatMapLatest { destination ->
        when (destination) {
            is ScreenDestination.Detail -> repository.observeVideoLinks(destination.performerId)
            ScreenDestination.Catalog -> flowOf(emptyList())
        }
    }

    val uiState: StateFlow<VideothekUiState> = combine(
        repository.observeAllPerformers()
            .catch { throwable ->
                seedErrorState.value = throwable.localizedMessage ?: "Datenbankfehler beim Laden."
                emit(emptyList())
            },
        selectedVideoLinksFlow,
        destinationState,
        lastFocusedIdState,
        filterCriteriaState,
        showAddDialogState,
        statusMessageState,
        seedErrorState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allPerformers = values[0] as List<Performer>
        @Suppress("UNCHECKED_CAST")
        val selectedLinks = values[1] as List<VideoLink>
        val destination = values[2] as ScreenDestination
        val lastFocusedId = values[3] as Long?
        val criteria = values[4] as FilterCriteria
        val showAddDialog = values[5] as Boolean
        val statusMessage = values[6] as String?
        val seedError = values[7] as String?

        val selectedPerformer = when (destination) {
            is ScreenDestination.Detail -> allPerformers.find { it.id == destination.performerId }
            ScreenDestination.Catalog -> null
        }

        val contentState = if (seedError != null && allPerformers.isEmpty()) {
            CatalogContentState.Error(seedError)
        } else {
            val filtered = applySortingAndFilters(allPerformers, criteria)
            CatalogContentState.Ready(
                performers = filtered,
                totalCount = allPerformers.size,
                totalVideoLinks = allPerformers.sumOf { it.videoCount },
            )
        }

        VideothekUiState(
            contentState = contentState,
            destination = destination,
            selectedPerformer = selectedPerformer,
            selectedPerformerVideoLinks = selectedLinks,
            lastFocusedPerformerId = lastFocusedId,
            sortOrder = criteria.sortOrder,
            alphabetBucket = criteria.alphabetBucket,
            searchQuery = criteria.searchQuery,
            isSearchActive = criteria.isSearchActive,
            showAddPerformerDialog = showAddDialog,
            statusMessage = statusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideothekUiState(),
    )

    init {
        initializeCatalog()
    }

    fun initializeCatalog() {
        viewModelScope.launch {
            seedErrorState.value = null
            runCatching {
                repository.ensureSeeded()
            }.onFailure { throwable ->
                seedErrorState.value = throwable.localizedMessage ?: "Initialisierung fehlgeschlagen."
            }
        }
    }

    fun openPerformerDetail(performerId: Long) {
        lastFocusedIdState.value = performerId
        destinationState.value = ScreenDestination.Detail(performerId)
    }

    fun navigateBackToCatalog() {
        destinationState.value = ScreenDestination.Catalog
    }

    fun updateLastFocusedPerformer(performerId: Long) {
        lastFocusedIdState.value = performerId
    }

    fun setSortOrder(sortOrder: SortOrder) {
        filterCriteriaState.update { it.copy(sortOrder = sortOrder) }
    }

    fun cycleSortOrder() {
        filterCriteriaState.update { current ->
            val entries = SortOrder.entries
            val next = entries[(entries.indexOf(current.sortOrder) + 1) % entries.size]
            current.copy(sortOrder = next)
        }
    }

    fun setAlphabetBucket(bucket: AlphabetBucket) {
        filterCriteriaState.update { it.copy(alphabetBucket = bucket) }
    }

    fun toggleSearchBar() {
        filterCriteriaState.update { current ->
            val nextActive = !current.isSearchActive
            current.copy(
                isSearchActive = nextActive,
                searchQuery = if (nextActive) current.searchQuery else "",
            )
        }
    }

    fun setSearchQuery(query: String) {
        filterCriteriaState.update { it.copy(searchQuery = query) }
    }

    fun resetFilters() {
        filterCriteriaState.value = FilterCriteria(
            sortOrder = SortOrder.ALPHA_ASC,
            alphabetBucket = AlphabetBucket.ALL,
            searchQuery = "",
            isSearchActive = false,
        )
    }

    fun setAddPerformerDialogVisible(visible: Boolean) {
        showAddDialogState.value = visible
    }

    fun addPerformer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val newId = repository.addPerformer(trimmed)
                lastFocusedIdState.value = newId
                showAddDialogState.value = false
                statusMessageState.value = "$trimmed hinzugefügt"
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Konnte nicht gespeichert werden"
            }
        }
    }

    fun updatePerformerImage(id: Long, imageUri: String?) {
        viewModelScope.launch {
            runCatching {
                repository.updateImageUri(id = id, imageUri = imageUri)
                statusMessageState.value = if (imageUri.isNullOrBlank()) {
                    "Bild entfernt"
                } else {
                    "Bild gespeichert"
                }
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Bild konnte nicht gespeichert werden"
            }
        }
    }

    fun renamePerformer(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.renamePerformer(id = id, newName = trimmed)
                statusMessageState.value = "Name aktualisiert"
            }
        }
    }

    fun deletePerformer(id: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deletePerformer(id)
                destinationState.value = ScreenDestination.Catalog
                statusMessageState.value = "Eintrag gelöscht"
            }
        }
    }

    fun addVideoLink(performerId: Long, title: String, url: String, thumbnailUrl: String?) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.addVideoLink(
                    performerId = performerId,
                    title = title,
                    url = trimmedUrl,
                    thumbnailUrl = thumbnailUrl,
                )
                statusMessageState.value = "Video-Link gespeichert"
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Link konnte nicht gespeichert werden"
            }
        }
    }

    fun updateVideoLink(linkId: Long, title: String, url: String, thumbnailUrl: String?) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.updateVideoLink(
                    linkId = linkId,
                    title = title,
                    url = trimmedUrl,
                    thumbnailUrl = thumbnailUrl,
                )
                statusMessageState.value = "Video-Link aktualisiert"
            }
        }
    }

    fun deleteVideoLink(linkId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteVideoLink(linkId)
                statusMessageState.value = "Video-Link entfernt"
            }
        }
    }

    fun clearStatusMessage() {
        statusMessageState.value = null
    }

    private fun applySortingAndFilters(
        allPerformers: List<Performer>,
        criteria: FilterCriteria,
    ): List<Performer> {
        val query = criteria.searchQuery.trim()
        val filtered = allPerformers.filter { performer ->
            val matchesBucket = criteria.alphabetBucket.matches(performer.name)
            val matchesQuery = query.isEmpty() || performer.name.contains(query, ignoreCase = true)
            matchesBucket && matchesQuery
        }

        return when (criteria.sortOrder) {
            SortOrder.ALPHA_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.ALPHA_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOrder.VIDEOS_DESC -> filtered.sortedWith(
                compareByDescending<Performer> { it.videoCount }.thenBy { it.name.lowercase() }
            )
            SortOrder.VIDEOS_ASC -> filtered.sortedWith(
                compareBy<Performer> { it.videoCount }.thenBy { it.name.lowercase() }
            )
        }
    }

    class Factory(
        private val repository: PerformerRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideothekViewModel::class.java)) {
                return VideothekViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
