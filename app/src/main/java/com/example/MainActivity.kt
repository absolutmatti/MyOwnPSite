package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.VideothekDatabase
import com.example.data.repository.PerformerRepository
import com.example.ui.components.TvTextInputDialog
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.PerformerDetailScreen
import com.example.ui.theme.VideothekTvTheme
import com.example.ui.viewmodel.ScreenDestination
import com.example.ui.viewmodel.VideothekViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = VideothekDatabase.getInstance(applicationContext)
        val repository = PerformerRepository(
            performerDao = database.performerDao(),
            videoLinkDao = database.videoLinkDao(),
        )

        setContent {
            VideothekTvTheme {
                val factory = remember(repository) { VideothekViewModel.Factory(repository) }
                val viewModel: VideothekViewModel = viewModel(factory = factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.statusMessage) {
                    if (uiState.statusMessage != null) {
                        delay(3_000)
                        viewModel.clearStatusMessage()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = uiState.destination,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith
                                fadeOut(animationSpec = tween(180))
                        },
                        label = "videothek_screen_transition",
                    ) { destination ->
                        when (destination) {
                            ScreenDestination.Catalog -> {
                                CatalogScreen(
                                    uiState = uiState,
                                    onSelectPerformer = viewModel::openPerformerDetail,
                                    onPerformerFocused = viewModel::updateLastFocusedPerformer,
                                    onCycleSortOrder = viewModel::cycleSortOrder,
                                    onSelectSortOrder = viewModel::setSortOrder,
                                    onSelectAlphabetBucket = viewModel::setAlphabetBucket,
                                    onToggleSearch = viewModel::toggleSearchBar,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onOpenAddDialog = {
                                        viewModel.setAddPerformerDialogVisible(true)
                                    },
                                    onResetFilters = viewModel::resetFilters,
                                    onRetry = viewModel::initializeCatalog,
                                )
                            }

                            is ScreenDestination.Detail -> {
                                val performer = uiState.selectedPerformer
                                if (performer != null) {
                                    PerformerDetailScreen(
                                        performer = performer,
                                        videoLinks = uiState.selectedPerformerVideoLinks,
                                        onBack = viewModel::navigateBackToCatalog,
                                        onUpdateImageUri = { uri ->
                                            viewModel.updatePerformerImage(performer.id, uri)
                                        },
                                        onRename = { newName ->
                                            viewModel.renamePerformer(performer.id, newName)
                                        },
                                        onDelete = {
                                            viewModel.deletePerformer(performer.id)
                                        },
                                        onAddVideoLink = { title, url ->
                                            viewModel.addVideoLink(performer.id, title, url)
                                        },
                                        onUpdateVideoLink = viewModel::updateVideoLink,
                                        onDeleteVideoLink = viewModel::deleteVideoLink,
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        viewModel.navigateBackToCatalog()
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.showAddPerformerDialog) {
                        TvTextInputDialog(
                            title = stringResource(R.string.dialog_add_title),
                            label = stringResource(R.string.dialog_name_label),
                            initialValue = "",
                            confirmText = stringResource(R.string.dialog_confirm),
                            onConfirm = viewModel::addPerformer,
                            onDismiss = { viewModel.setAddPerformerDialogVisible(false) },
                        )
                    }
                }
            }
        }
    }
}
