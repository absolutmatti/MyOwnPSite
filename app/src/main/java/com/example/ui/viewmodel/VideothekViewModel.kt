package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PerformerRepository
import com.example.domain.model.AlphabetBucket
import com.example.domain.model.CatalogFilter
import com.example.domain.model.Performer
import com.example.domain.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CatalogContentState {
    data object Loading : CatalogContentState
    data class Ready(
        val performers: List<Performer>,
        val totalCount: Int,
        val withImageCount: Int,
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
    val lastFocusedPerformerId: Long? = null,
    val sortOrder: SortOrder = SortOrder.A_TO_Z,
    val catalogFilter: CatalogFilter = CatalogFilter.ALL,
    val alphabetBucket: AlphabetBucket = AlphabetBucket.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val showAddPerformerDialog: Boolean = false,
    val statusMessage: String? = null,
)

private data class FilterCriteria(
    val sortOrder: SortOrder,
    val catalogFilter: CatalogFilter,
    val alphabetBucket: AlphabetBucket,
    val searchQuery: String,
    val isSearchActive: Boolean,
)

class VideothekViewModel(
    private val repository: PerformerRepository,
) : ViewModel() {

    private val destinationState = MutableStateFlow<ScreenDestination>(ScreenDestination.Catalog)
    private val lastFocusedIdState = MutableStateFlow<Long?>(null)
    private val filterCriteriaState = MutableStateFlow(
        FilterCriteria(
            sortOrder = SortOrder.A_TO_Z,
            catalogFilter = CatalogFilter.ALL,
            alphabetBucket = AlphabetBucket.ALL,
            searchQuery = "",
            isSearchActive = false,
        )
    )
    private val showAddDialogState = MutableStateFlow(false)
    private val statusMessageState = MutableStateFlow<String?>(null)
    private val seedErrorState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<VideothekUiState> = combine(
        repository.observeAllPerformers()
            .catch { throwable ->
                seedErrorState.value = throwable.localizedMessage ?: "Datenbankfehler beim Laden."
                emit(emptyList())
            },
        destinationState,
        lastFocusedIdState,
        filterCriteriaState,
        showAddDialogState,
        statusMessageState,
        seedErrorState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allPerformers = values[0] as List<Performer>
        val destination = values[1] as ScreenDestination
        val lastFocusedId = values[2] as Long?
        val criteria = values[3] as FilterCriteria
        val showAddDialog = values[4] as Boolean
        val statusMessage = values[5] as String?
        val seedError = values[6] as String?

        val selectedPerformer = when (destination) {
            is ScreenDestination.Detail -> allPerformers.find { it.id == destination.performerId }
            ScreenDestination.Catalog -> null
        }

        val contentState = if (seedError != null && allPerformers.isEmpty()) {
            CatalogContentState.Error(seedError)
        } else {
            val filtered = applyFilters(allPerformers, criteria)
            CatalogContentState.Ready(
                performers = filtered,
                totalCount = allPerformers.size,
                withImageCount = allPerformers.count { it.hasImage },
            )
        }

        VideothekUiState(
            contentState = contentState,
            destination = destination,
            selectedPerformer = selectedPerformer,
            lastFocusedPerformerId = lastFocusedId,
            sortOrder = criteria.sortOrder,
            catalogFilter = criteria.catalogFilter,
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

    fun toggleSortOrder() {
        filterCriteriaState.update { current ->
            val next = if (current.sortOrder == SortOrder.A_TO_Z) {
                SortOrder.Z_TO_A
            } else {
                SortOrder.A_TO_Z
            }
            current.copy(sortOrder = next)
        }
    }

    fun setCatalogFilter(filter: CatalogFilter) {
        filterCriteriaState.update { it.copy(catalogFilter = filter) }
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
            sortOrder = SortOrder.A_TO_Z,
            catalogFilter = CatalogFilter.ALL,
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

    fun toggleFavorite(performer: Performer) {
        viewModelScope.launch {
            runCatching {
                repository.toggleFavorite(id = performer.id, currentFavorite = performer.isFavorite)
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

    fun clearStatusMessage() {
        statusMessageState.value = null
    }

    private fun applyFilters(
        allPerformers: List<Performer>,
        criteria: FilterCriteria,
    ): List<Performer> {
        val query = criteria.searchQuery.trim()
        val filtered = allPerformers.filter { performer ->
            val matchesFilter = when (criteria.catalogFilter) {
                CatalogFilter.ALL -> true
                CatalogFilter.FAVORITES -> performer.isFavorite
                CatalogFilter.WITHOUT_IMAGE -> !performer.hasImage
                CatalogFilter.WITH_IMAGE -> performer.hasImage
            }
            val matchesBucket = criteria.alphabetBucket.matches(performer.name)
            val matchesQuery = query.isEmpty() || performer.name.contains(query, ignoreCase = true)
            matchesFilter && matchesBucket && matchesQuery
        }

        return when (criteria.sortOrder) {
            SortOrder.A_TO_Z -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.Z_TO_A -> filtered.sortedByDescending { it.name.lowercase() }
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
