package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.R
import com.example.domain.model.AlphabetBucket
import com.example.domain.model.SortOrder
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CinemaAmberLight
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.CinemaGoldContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCatalogHeader(
    visibleCount: Int,
    totalCount: Int,
    totalVideoLinks: Int,
    sortOrder: SortOrder,
    alphabetBucket: AlphabetBucket,
    isSearchActive: Boolean,
    searchQuery: String,
    onCycleSortOrder: () -> Unit,
    onSelectSortOrder: (SortOrder) -> Unit,
    onSelectAlphabetBucket: (AlphabetBucket) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Brand Row + Primary Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(CinemaGold),
                )
                Column {
                    Text(
                        text = stringResource(R.string.tv_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "$visibleCount von $totalCount Darstellerinnen • $totalVideoLinks Video-Links",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCycleSortOrder,
                    modifier = Modifier.testTag("sort_toggle_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = CardSurfaceElevated,
                        contentColor = TextPrimary,
                        focusedContainerColor = CinemaGold,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sortOrder.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("search_toggle_button"),
                    colors = IconButtonDefaults.colors(
                        containerColor = if (isSearchActive) CinemaGoldContainer else CardSurfaceElevated,
                        contentColor = if (isSearchActive) CinemaAmberLight else TextPrimary,
                        focusedContainerColor = CinemaGold,
                        focusedContentColor = ObsidianBackground,
                    ),
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(R.string.action_search),
                    )
                }

                Button(
                    onClick = onOpenAddDialog,
                    modifier = Modifier.testTag("add_performer_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaGoldContainer,
                        contentColor = CinemaAmberLight,
                        focusedContainerColor = CinemaGold,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_add_performer),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // Optional Search Field
        AnimatedVisibility(visible = isSearchActive) {
            TvSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClear = { onSearchQueryChange("") },
            )
        }

        // Sort Options (Alphabet & Video Count) + Alphabet Jump Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(SortOrder.entries) { order ->
                TvSelectableChip(
                    label = order.label,
                    selected = sortOrder == order,
                    onClick = { onSelectSortOrder(order) },
                    testTag = "sort_chip_${order.name.lowercase()}",
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(22.dp)
                        .background(BorderSubtle),
                )
            }

            items(AlphabetBucket.entries) { bucket ->
                TvSelectableChip(
                    label = if (bucket == AlphabetBucket.ALL) "Alle" else bucket.label,
                    selected = alphabetBucket == bucket,
                    onClick = { onSelectAlphabetBucket(bucket) },
                    testTag = "alphabet_chip_${bucket.name.lowercase()}",
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        colors = FilterChipDefaults.colors(
            containerColor = CardSurface,
            contentColor = TextSecondary,
            selectedContainerColor = CinemaGoldContainer,
            selectedContentColor = CinemaAmberLight,
            focusedContainerColor = CinemaGold,
            focusedContentColor = ObsidianBackground,
            focusedSelectedContainerColor = CinemaGold,
            focusedSelectedContentColor = ObsidianBackground,
        ),
        border = FilterChipDefaults.border(
            border = Border(BorderStroke(1.dp, BorderSubtle)),
            selectedBorder = Border(BorderStroke(1.dp, CinemaGold.copy(alpha = 0.6f))),
            focusedBorder = Border(BorderStroke(2.dp, CinemaAmberLight)),
            focusedSelectedBorder = Border(BorderStroke(2.dp, TextPrimary)),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurfaceElevated)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) CinemaGold else BorderSubtle,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (isFocused) CinemaGold else TextSecondary,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                cursorBrush = SolidColor(CinemaGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag("search_input"),
            )
        }

        if (query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.colors(
                    containerColor = CardSurface,
                    contentColor = TextSecondary,
                    focusedContainerColor = CinemaGold,
                    focusedContentColor = ObsidianBackground,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.dialog_cancel),
                )
            }
        }
    }
}
