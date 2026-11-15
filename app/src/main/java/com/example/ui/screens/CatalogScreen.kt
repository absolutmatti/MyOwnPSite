package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.R
import com.example.domain.model.AlphabetBucket
import com.example.domain.model.Performer
import com.example.domain.model.SortOrder
import com.example.ui.components.PerformerTileCard
import com.example.ui.components.TvCatalogHeader
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CinemaAmberLight
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianGradientTop
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CatalogContentState
import com.example.ui.viewmodel.VideothekUiState

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogScreen(
    uiState: VideothekUiState,
    onSelectPerformer: (Long) -> Unit,
    onPerformerFocused: (Long) -> Unit,
    onCycleSortOrder: () -> Unit,
    onSelectSortOrder: (SortOrder) -> Unit,
    onSelectAlphabetBucket: (AlphabetBucket) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddDialog: () -> Unit,
    onResetFilters: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedPerformer by remember { mutableStateOf<Performer?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ObsidianGradientTop, ObsidianBackground),
                )
            ),
    ) {
        // 5% Overscan safe area for TV screens, adaptive on smaller displays
        val horizontalPadding = if (maxWidth >= 840.dp) 48.dp else 20.dp
        val verticalPadding = if (maxHeight >= 540.dp) 28.dp else 16.dp
        val columnCount = when {
            maxWidth >= 1120.dp -> 6
            maxWidth >= 840.dp -> 5
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val readyState = uiState.contentState as? CatalogContentState.Ready

            TvCatalogHeader(
                visibleCount = readyState?.performers?.size ?: 0,
                totalCount = readyState?.totalCount ?: 0,
                totalVideoLinks = readyState?.totalVideoLinks ?: 0,
                sortOrder = uiState.sortOrder,
                alphabetBucket = uiState.alphabetBucket,
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                onCycleSortOrder = onCycleSortOrder,
                onSelectSortOrder = onSelectSortOrder,
                onSelectAlphabetBucket = onSelectAlphabetBucket,
                onToggleSearch = onToggleSearch,
                onSearchQueryChange = onSearchQueryChange,
                onOpenAddDialog = onOpenAddDialog,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val content = uiState.contentState) {
                    CatalogContentState.Loading -> {
                        LoadingCatalogState()
                    }

                    is CatalogContentState.Error -> {
                        ErrorCatalogState(
                            message = content.message,
                            onRetry = onRetry,
                        )
                    }

                    is CatalogContentState.Ready -> {
                        if (content.performers.isEmpty()) {
                            EmptyCatalogState(
                                onResetFilters = onResetFilters,
                            )
                        } else {
                            PerformerGrid(
                                performers = content.performers,
                                columnCount = columnCount,
                                targetFocusPerformerId = uiState.lastFocusedPerformerId,
                                onSelectPerformer = onSelectPerformer,
                                onPerformerFocused = { performer ->
                                    focusedPerformer = performer
                                    onPerformerFocused(performer.id)
                                },
                            )
                        }
                    }
                }
            }

            // Bottom Focus Status Strip
            FocusStatusFooter(
                focusedPerformer = focusedPerformer,
                sortOrder = uiState.sortOrder,
                statusMessage = uiState.statusMessage,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PerformerGrid(
    performers: List<Performer>,
    columnCount: Int,
    targetFocusPerformerId: Long?,
    onSelectPerformer: (Long) -> Unit,
    onPerformerFocused: (Performer) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val targetFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    val targetIndex = remember(performers, targetFocusPerformerId) {
        if (targetFocusPerformerId == null) -1
        else performers.indexOfFirst { it.id == targetFocusPerformerId }
    }

    LaunchedEffect(performers.size, targetFocusPerformerId) {
        if (performers.isNotEmpty() && !hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            runCatching {
                if (targetIndex >= 0) {
                    gridState.scrollToItem(targetIndex)
                    targetFocusRequester.requestFocus()
                } else {
                    firstItemFocusRequester.requestFocus()
                }
            }
        }
    }

    val bringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val targetLeading = (containerSize * 0.28f) - (size * 0.5f)
                val clampedTarget = targetLeading.coerceIn(0f, (containerSize - size).coerceAtLeast(0f))
                return offset - clampedTarget
            }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier
                .fillMaxSize()
                .testTag("performer_grid"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(
                items = performers,
                key = { _, performer -> performer.id },
            ) { index, performer ->
                val focusModifier = when {
                    performer.id == targetFocusPerformerId -> Modifier.focusRequester(targetFocusRequester)
                    index == 0 -> Modifier.focusRequester(firstItemFocusRequester)
                    else -> Modifier
                }

                PerformerTileCard(
                    performer = performer,
                    onClick = { onSelectPerformer(performer.id) },
                    onFocused = { onPerformerFocused(performer) },
                    modifier = focusModifier,
                )
            }
        }
    }
}

@Composable
private fun FocusStatusFooter(
    focusedPerformer: Performer?,
    sortOrder: SortOrder,
    statusMessage: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface.copy(alpha = 0.85f))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (focusedPerformer != null) {
            val linkText = if (focusedPerformer.videoCount == 1) "1 Video-Link"
            else "${focusedPerformer.videoCount} Video-Links"
            val imageText = if (focusedPerformer.hasImage) "9:16-Bild aktiv" else "Leere 9:16-Kachel"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = focusedPerformer.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaAmberLight,
                )
                Text(
                    text = "• $linkText • $imageText • OK für Details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        } else {
            Text(
                text = "D-Pad zum Navigieren • OK auf Kachel drücken für Bild & Video-Links",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        Text(
            text = statusMessage ?: "Sortierung: ${sortOrder.label}",
            style = MaterialTheme.typography.labelLarge,
            color = if (statusMessage != null) CinemaGold else TextSecondary,
        )
    }
}

@Composable
private fun LoadingCatalogState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = CinemaGold)
            Text(
                text = "Videothek wird geladen…",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorCatalogState(
    message: String,
    onRetry: () -> Unit,
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        retryFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .focusRequester(retryFocusRequester)
                    .testTag("retry_button"),
                colors = ButtonDefaults.colors(
                    containerColor = CinemaGold,
                    contentColor = ObsidianBackground,
                    focusedContainerColor = TextPrimary,
                    focusedContentColor = ObsidianBackground,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyCatalogState(
    onResetFilters: () -> Unit,
) {
    val resetFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        resetFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = CinemaGold,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.empty_state_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onResetFilters,
                modifier = Modifier
                    .focusRequester(resetFocusRequester)
                    .testTag("reset_filters_button"),
                colors = ButtonDefaults.colors(
                    containerColor = CardSurfaceElevated,
                    contentColor = TextPrimary,
                    focusedContainerColor = CinemaGold,
                    focusedContentColor = ObsidianBackground,
                ),
            ) {
                Text(text = stringResource(R.string.reset_filters))
            }
        }
    }
}
