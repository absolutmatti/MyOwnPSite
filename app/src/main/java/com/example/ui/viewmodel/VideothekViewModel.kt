package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PerformerRepository
import com.example.domain.model.AlphabetBucket
import com.example.domain.model.Performer
import com.example.domain.model.SortOrder
import com.example.domain.model.VideoLink
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
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
    data class Detail(val performerId: String) : ScreenDestination
}

data class VideothekUiState(
    val contentState: CatalogContentState = CatalogContentState.Loading,
    val destination: ScreenDestination = ScreenDestination.Catalog,
    val selectedPerformer: Performer? = null,
    val selectedPerformerVideoLinks: List<VideoLink> = emptyList(),
    val lastFocusedPerformerId: String? = null,
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
    private val currentUserId: String,
) : ViewModel() {

    private val destinationState = MutableStateFlow<ScreenDestination>(ScreenDestination.Catalog)
    private val lastFocusedIdState = MutableStateFlow<String?>(null)
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
    private val streamErrorState = MutableStateFlow<String?>(null)
    private val hasReceivedSnapshotState = MutableStateFlow(false)

    private val performersStream = repository.observeAllPerformers(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing performers", rootError)
            streamErrorState.value = rootError.localizedMessage ?: "Cloud-Sync Fehler beim Laden."
            emit(emptyList())
        }

    private val selectedVideoLinksFlow = destinationState.flatMapLatest { destination ->
        when (destination) {
            is ScreenDestination.Detail -> repository.observeVideoLinks(currentUserId, destination.performerId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing video links", rootError)
                    statusMessageState.value = rootError.localizedMessage ?: "Fehler beim Laden der Video-Links"
                    emit(emptyList())
                }
            ScreenDestination.Catalog -> flowOf(emptyList())
        }
    }

    val uiState: StateFlow<VideothekUiState> = combine(
        performersStream,
        selectedVideoLinksFlow,
        destinationState,
        lastFocusedIdState,
        filterCriteriaState,
        showAddDialogState,
        statusMessageState,
        streamErrorState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allPerformers = values[0] as List<Performer>
        @Suppress("UNCHECKED_CAST")
        val selectedLinks = values[1] as List<VideoLink>
        val destination = values[2] as ScreenDestination
        val lastFocusedId = values[3] as String?
        val criteria = values[4] as FilterCriteria
        val showAddDialog = values[5] as Boolean
        val statusMessage = values[6] as String?
        val streamError = values[7] as String?

        if (allPerformers.isNotEmpty()) {
            hasReceivedSnapshotState.value = true
        }

        val selectedPerformer = when (destination) {
            is ScreenDestination.Detail -> allPerformers.find { it.id == destination.performerId }
            ScreenDestination.Catalog -> null
        }

        val contentState = when {
            streamError != null && allPerformers.isEmpty() -> CatalogContentState.Error(streamError)
            !hasReceivedSnapshotState.value && allPerformers.isEmpty() -> CatalogContentState.Loading
            else -> {
                val filtered = applySortingAndFilters(allPerformers, criteria)
                CatalogContentState.Ready(
                    performers = filtered,
                    totalCount = allPerformers.size,
                    totalVideoLinks = allPerformers.sumOf { it.videoCount },
                )
            }
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
            streamErrorState.value = null
            runCatching {
                repository.ensureSeeded()
                hasReceivedSnapshotState.value = true
            }.onFailure { throwable ->
                val firestoreError = generateSequence(throwable) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && throwable is CancellationException) throw throwable
                val rootError = firestoreError ?: throwable
                Log.w(TAG, "Error seeding catalog", rootError)
                streamErrorState.value = rootError.localizedMessage ?: "Cloud-Initialisierung fehlgeschlagen."
            }
        }
    }

    fun openPerformerDetail(performerId: String) {
        lastFocusedIdState.value = performerId
        destinationState.value = ScreenDestination.Detail(performerId)
    }

    fun navigateBackToCatalog() {
        destinationState.value = ScreenDestination.Catalog
    }

    fun updateLastFocusedPerformer(performerId: String) {
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
                statusMessageState.value = "$trimmed synchronisiert"
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Konnte nicht gespeichert werden"
            }
        }
    }

    fun updatePerformerImage(id: String, imageUri: String?) {
        viewModelScope.launch {
            runCatching {
                repository.updateImageUri(id = id, imageUri = imageUri)
                statusMessageState.value = if (imageUri.isNullOrBlank()) {
                    "Bild entfernt"
                } else {
                    "Bild gespeichert & synchronisiert"
                }
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Bild konnte nicht gespeichert werden"
            }
        }
    }

    fun renamePerformer(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.renamePerformer(id = id, newName = trimmed)
                statusMessageState.value = "Name aktualisiert"
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Name konnte nicht geändert werden"
            }
        }
    }

    fun deletePerformer(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.deletePerformer(id)
                destinationState.value = ScreenDestination.Catalog
                statusMessageState.value = "Eintrag gelöscht"
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Löschen fehlgeschlagen"
            }
        }
    }

    fun addVideoLink(performerId: String, title: String, url: String, thumbnailUrl: String?) {
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
                statusMessageState.value = "Video-Link synchronisiert"
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Link konnte nicht gespeichert werden"
            }
        }
    }

    fun updateVideoLink(linkId: String, title: String, url: String, thumbnailUrl: String?) {
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
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Link konnte nicht aktualisiert werden"
            }
        }
    }

    fun deleteVideoLink(linkId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteVideoLink(linkId)
                statusMessageState.value = "Video-Link entfernt"
            }.onFailure { throwable ->
                statusMessageState.value = throwable.localizedMessage ?: "Link konnte nicht gelöscht werden"
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

    private companion object {
        const val TAG = "VideothekVM"
    }
}
