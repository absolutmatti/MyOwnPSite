package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.PerformerRepository
import com.example.ui.components.TvTextInputDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.PerformerDetailScreen
import com.example.ui.screens.attemptAutoSignIn
import com.example.ui.screens.signOutUser
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CinemaAmberLight
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.VideothekTvTheme
import com.example.ui.viewmodel.ScreenDestination
import com.example.ui.viewmodel.VideothekViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VideothekTvTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isCheckingAutoSignIn by remember { mutableStateOf(Firebase.auth.currentUser == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isCheckingAutoSignIn = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    isCheckingAutoSignIn = false
                },
                onUnauthenticated = {
                    isCheckingAutoSignIn = false
                },
                scope = coroutineScope,
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            isCheckingAutoSignIn = isCheckingAutoSignIn,
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
        )
    } else {
        key(user.uid) {
            AuthenticatedVideothekContent(
                currentUserId = user.uid,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = coroutineScope,
                        onSignOutComplete = {
                            currentUser = null
                            isCheckingAutoSignIn = false
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AuthenticatedVideothekContent(
    currentUserId: String,
    onSignOut: () -> Unit,
) {
    val viewModel: VideothekViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VideothekViewModel(
                    repository = PerformerRepository(db),
                    currentUserId = currentUserId,
                )
            }
        },
    )

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
                        onSignOut = onSignOut,
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
                            onAddVideoLink = { title, url, thumbnailUrl ->
                                viewModel.addVideoLink(performer.id, title, url, thumbnailUrl)
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

        AnimatedVisibility(
            visible = uiState.statusMessage != null && uiState.destination is ScreenDestination.Detail,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        ) {
            uiState.statusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardSurfaceElevated)
                        .border(1.5.dp, CinemaGold, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaAmberLight,
                    )
                }
            }
        }
    }
}
