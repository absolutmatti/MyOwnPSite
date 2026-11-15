package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.R
import com.example.domain.model.Performer
import com.example.ui.components.PerformerPortraitFrame
import com.example.ui.components.TvConfirmDeleteDialog
import com.example.ui.components.TvTextInputDialog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CinemaAmberLight
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.CinemaGoldContainer
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianGradientTop
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PerformerDetailScreen(
    performer: Performer,
    onBack: () -> Unit,
    onUpdateImageUri: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val primaryActionFocusRequester = remember { FocusRequester() }

    var showUrlDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pickerUnavailableHint by remember { mutableStateOf<String?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onUpdateImageUri(uri.toString())
            pickerUnavailableHint = null
        }
    }

    LaunchedEffect(performer.id) {
        runCatching {
            primaryActionFocusRequester.requestFocus()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ObsidianGradientTop, ObsidianBackground),
                )
            ),
    ) {
        val isWide = maxWidth >= 700.dp
        val horizontalPadding = if (isWide) 52.dp else 20.dp
        val verticalPadding = if (maxHeight >= 520.dp) 32.dp else 16.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: Large 9:16 Portrait Preview Card
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.86f)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(2.dp, CinemaGold.copy(alpha = 0.65f), RoundedCornerShape(18.dp)),
                    ) {
                        PerformerPortraitFrame(
                            performer = performer,
                            isFocused = true,
                            showFocusHint = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Right: Performer Metadata & D-Pad Action List
                    DetailActionsColumn(
                        performer = performer,
                        primaryActionFocusRequester = primaryActionFocusRequester,
                        pickerUnavailableHint = pickerUnavailableHint,
                        onBack = onBack,
                        onPickLocalImage = {
                            try {
                                documentPickerLauncher.launch(arrayOf("image/*"))
                            } catch (_: ActivityNotFoundException) {
                                pickerUnavailableHint =
                                    "Auf diesem TV-Gerät ist kein Datei-Picker installiert. Nutze stattdessen 'Bild-URL / Pfad eingeben'."
                            }
                        },
                        onOpenUrlDialog = { showUrlDialog = true },
                        onRemoveImage = { onUpdateImageUri(null) },
                        onToggleFavorite = onToggleFavorite,
                        onOpenRenameDialog = { showRenameDialog = true },
                        onOpenDeleteDialog = { showDeleteDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .height(280.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, CinemaGold.copy(alpha = 0.65f), RoundedCornerShape(16.dp)),
                    ) {
                        PerformerPortraitFrame(
                            performer = performer,
                            isFocused = true,
                            showFocusHint = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    DetailActionsColumn(
                        performer = performer,
                        primaryActionFocusRequester = primaryActionFocusRequester,
                        pickerUnavailableHint = pickerUnavailableHint,
                        onBack = onBack,
                        onPickLocalImage = {
                            try {
                                documentPickerLauncher.launch(arrayOf("image/*"))
                            } catch (_: ActivityNotFoundException) {
                                pickerUnavailableHint =
                                    "Kein Datei-Picker verfügbar. Nutze 'Bild-URL / Pfad eingeben'."
                            }
                        },
                        onOpenUrlDialog = { showUrlDialog = true },
                        onRemoveImage = { onUpdateImageUri(null) },
                        onToggleFavorite = onToggleFavorite,
                        onOpenRenameDialog = { showRenameDialog = true },
                        onOpenDeleteDialog = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        TvTextInputDialog(
            title = stringResource(R.string.dialog_url_title),
            label = stringResource(R.string.dialog_url_label),
            initialValue = performer.imageUri.orEmpty(),
            confirmText = stringResource(R.string.dialog_confirm),
            onConfirm = { url ->
                onUpdateImageUri(url)
                showUrlDialog = false
            },
            onDismiss = { showUrlDialog = false },
        )
    }

    if (showRenameDialog) {
        TvTextInputDialog(
            title = stringResource(R.string.detail_edit_name),
            label = stringResource(R.string.dialog_name_label),
            initialValue = performer.name,
            confirmText = stringResource(R.string.dialog_confirm),
            onConfirm = { updatedName ->
                onRename(updatedName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        TvConfirmDeleteDialog(
            performerName = performer.name,
            onConfirmDelete = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailActionsColumn(
    performer: Performer,
    primaryActionFocusRequester: FocusRequester,
    pickerUnavailableHint: String?,
    onBack: () -> Unit,
    onPickLocalImage: () -> Unit,
    onOpenUrlDialog: () -> Unit,
    onRemoveImage: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenRenameDialog: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.testTag("detail_back_button"),
            colors = ButtonDefaults.colors(
                containerColor = CardSurfaceElevated,
                contentColor = TextPrimary,
                focusedContainerColor = CinemaGold,
                focusedContentColor = ObsidianBackground,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.detail_back),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.detail_back))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = performer.name,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                text = if (performer.hasImage) "9:16-Bild hinterlegt" else "Leere 9:16-Kachel",
                highlighted = performer.hasImage,
            )
            if (performer.isFavorite) {
                StatusPill(text = "Favorit", highlighted = true)
            }
        }

        if (pickerUnavailableHint != null) {
            Text(
                text = pickerUnavailableHint,
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaAmberLight,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaGoldContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        DetailActionButton(
            icon = Icons.Filled.AddPhotoAlternate,
            label = stringResource(R.string.detail_pick_local_image),
            isPrimary = true,
            onClick = onPickLocalImage,
            modifier = Modifier
                .focusRequester(primaryActionFocusRequester)
                .testTag("pick_local_image_button"),
        )

        DetailActionButton(
            icon = Icons.Filled.Link,
            label = stringResource(R.string.detail_enter_image_url),
            isPrimary = false,
            onClick = onOpenUrlDialog,
            modifier = Modifier.testTag("enter_image_url_button"),
        )

        DetailActionButton(
            icon = if (performer.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            label = stringResource(
                if (performer.isFavorite) R.string.detail_toggle_favorite_on
                else R.string.detail_toggle_favorite_off
            ),
            isPrimary = false,
            onClick = onToggleFavorite,
            modifier = Modifier.testTag("toggle_favorite_button"),
        )

        DetailActionButton(
            icon = Icons.Filled.Edit,
            label = stringResource(R.string.detail_edit_name),
            isPrimary = false,
            onClick = onOpenRenameDialog,
            modifier = Modifier.testTag("rename_performer_button"),
        )

        if (performer.hasImage) {
            DetailActionButton(
                icon = Icons.Filled.HideImage,
                label = stringResource(R.string.detail_remove_image),
                isPrimary = false,
                onClick = onRemoveImage,
                modifier = Modifier.testTag("remove_image_button"),
            )
        }

        DetailActionButton(
            icon = Icons.Filled.DeleteOutline,
            label = stringResource(R.string.detail_delete_performer),
            isPrimary = false,
            isDanger = true,
            onClick = onOpenDeleteDialog,
            modifier = Modifier.testTag("delete_performer_button"),
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    highlighted: Boolean,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) CinemaGoldContainer else CardSurface)
            .border(
                width = 1.dp,
                color = if (highlighted) CinemaGold.copy(alpha = 0.6f) else BorderSubtle,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) CinemaAmberLight else TextSecondary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
) {
    val containerColor = when {
        isDanger -> DangerRedContainer
        isPrimary -> CinemaGoldContainer
        else -> CardSurface
    }
    val contentColor = when {
        isDanger -> DangerRed
        isPrimary -> CinemaAmberLight
        else -> TextPrimary
    }
    val focusedContainer = if (isDanger) DangerRed else CinemaGold

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(0.85f),
        colors = ButtonDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainer,
            focusedContentColor = ObsidianBackground,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
