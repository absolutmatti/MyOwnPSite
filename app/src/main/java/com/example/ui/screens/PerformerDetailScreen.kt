package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.Performer
import com.example.domain.model.VideoLink
import com.example.ui.components.ImageDataCodec
import com.example.ui.components.PerformerPortraitFrame
import com.example.ui.components.TvConfirmDeleteDialog
import com.example.ui.components.TvTextInputDialog
import com.example.ui.components.TvVideoLinkDialog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardEmptyGradientBottom
import com.example.ui.theme.CardEmptyGradientTop
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CinemaAmberLight
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.CinemaGoldContainer
import com.example.ui.theme.CinemaGoldGlow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianGradientTop
import com.example.ui.theme.ScrimDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PerformerDetailScreen(
    performer: Performer,
    videoLinks: List<VideoLink>,
    onBack: () -> Unit,
    onUpdateImageUri: (String?) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddVideoLink: (title: String, url: String, thumbnailUrl: String?) -> Unit,
    onUpdateVideoLink: (linkId: String, title: String, url: String, thumbnailUrl: String?) -> Unit,
    onDeleteVideoLink: (linkId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val primaryActionFocusRequester = remember { FocusRequester() }

    var showUrlDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddVideoLinkDialog by remember { mutableStateOf(false) }
    var editingVideoLink by remember { mutableStateOf<VideoLink?>(null) }
    var feedbackHint by remember { mutableStateOf<String?>(null) }

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
            val syncedDataUrl = ImageDataCodec.compressImageUriToDataUrl(context, uri)
            onUpdateImageUri(syncedDataUrl ?: uri.toString())
            feedbackHint = null
        }
    }

    LaunchedEffect(performer.id) {
        runCatching {
            primaryActionFocusRequester.requestFocus()
        }
    }

    fun openVideoUrl(rawUrl: String) {
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://") ||
            rawUrl.startsWith("content://") || rawUrl.startsWith("file://")
        ) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
            context.startActivity(intent)
            feedbackHint = null
        } catch (_: ActivityNotFoundException) {
            feedbackHint = "Keine App bzw. kein Browser zum Öffnen von \"$normalized\" auf diesem Gerät installiert."
        } catch (_: IllegalArgumentException) {
            feedbackHint = "Ungültiger Video-Link: \"$rawUrl\""
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
                    verticalAlignment = Alignment.Top,
                ) {
                    // Left: Large 9:16 Portrait Preview Card
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.88f)
                            .aspectRatio(9f / 16f)
                            .align(Alignment.CenterVertically)
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

                    // Right: Performer Metadata, Video Thumbnails & Image Actions
                    DetailContentColumn(
                        performer = performer,
                        videoLinks = videoLinks,
                        isWideLayout = true,
                        primaryActionFocusRequester = primaryActionFocusRequester,
                        feedbackHint = feedbackHint,
                        onBack = onBack,
                        onOpenAddVideoLinkDialog = { showAddVideoLinkDialog = true },
                        onOpenVideoLink = { link -> openVideoUrl(link.url) },
                        onEditVideoLink = { link -> editingVideoLink = link },
                        onDeleteVideoLink = { link -> onDeleteVideoLink(link.id) },
                        onPickLocalImage = {
                            try {
                                documentPickerLauncher.launch(arrayOf("image/*"))
                            } catch (_: ActivityNotFoundException) {
                                feedbackHint =
                                    "Auf diesem TV-Gerät ist kein Datei-Picker installiert. Nutze stattdessen 'Bild-URL / Pfad eingeben'."
                            }
                        },
                        onOpenUrlDialog = { showUrlDialog = true },
                        onRemoveImage = { onUpdateImageUri(null) },
                        onOpenRenameDialog = { showRenameDialog = true },
                        onOpenDeleteDialog = { showDeleteDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
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

                    DetailContentColumn(
                        performer = performer,
                        videoLinks = videoLinks,
                        isWideLayout = false,
                        primaryActionFocusRequester = primaryActionFocusRequester,
                        feedbackHint = feedbackHint,
                        onBack = onBack,
                        onOpenAddVideoLinkDialog = { showAddVideoLinkDialog = true },
                        onOpenVideoLink = { link -> openVideoUrl(link.url) },
                        onEditVideoLink = { link -> editingVideoLink = link },
                        onDeleteVideoLink = { link -> onDeleteVideoLink(link.id) },
                        onPickLocalImage = {
                            try {
                                documentPickerLauncher.launch(arrayOf("image/*"))
                            } catch (_: ActivityNotFoundException) {
                                feedbackHint =
                                    "Kein Datei-Picker verfügbar. Nutze 'Bild-URL / Pfad eingeben'."
                            }
                        },
                        onOpenUrlDialog = { showUrlDialog = true },
                        onRemoveImage = { onUpdateImageUri(null) },
                        onOpenRenameDialog = { showRenameDialog = true },
                        onOpenDeleteDialog = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showAddVideoLinkDialog) {
        TvVideoLinkDialog(
            title = stringResource(R.string.dialog_video_link_add_title),
            initialTitle = "",
            initialUrl = "",
            initialThumbnailUrl = "",
            confirmText = stringResource(R.string.dialog_confirm),
            onConfirm = { title, url, thumbnailUrl ->
                onAddVideoLink(title, url, thumbnailUrl)
                showAddVideoLinkDialog = false
            },
            onDismiss = { showAddVideoLinkDialog = false },
        )
    }

    editingVideoLink?.let { currentLink ->
        TvVideoLinkDialog(
            title = stringResource(R.string.dialog_video_link_edit_title),
            initialTitle = currentLink.title,
            initialUrl = currentLink.url,
            initialThumbnailUrl = currentLink.thumbnailUrl.orEmpty(),
            confirmText = stringResource(R.string.dialog_confirm),
            onConfirm = { title, url, thumbnailUrl ->
                onUpdateVideoLink(currentLink.id, title, url, thumbnailUrl)
                editingVideoLink = null
            },
            onDismiss = { editingVideoLink = null },
        )
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
private fun DetailContentColumn(
    performer: Performer,
    videoLinks: List<VideoLink>,
    isWideLayout: Boolean,
    primaryActionFocusRequester: FocusRequester,
    feedbackHint: String?,
    onBack: () -> Unit,
    onOpenAddVideoLinkDialog: () -> Unit,
    onOpenVideoLink: (VideoLink) -> Unit,
    onEditVideoLink: (VideoLink) -> Unit,
    onDeleteVideoLink: (VideoLink) -> Unit,
    onPickLocalImage: () -> Unit,
    onOpenUrlDialog: () -> Unit,
    onRemoveImage: () -> Unit,
    onOpenRenameDialog: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 24.dp),
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
                text = if (videoLinks.size == 1) "1 Video" else "${videoLinks.size} Videos",
                highlighted = videoLinks.isNotEmpty(),
            )
            StatusPill(
                text = if (performer.hasImage) "9:16-Bild hinterlegt" else "Leere 9:16-Kachel",
                highlighted = performer.hasImage,
            )
        }

        if (feedbackHint != null) {
            Text(
                text = feedbackHint,
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaAmberLight,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaGoldContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        DetailActionButton(
            icon = Icons.Filled.AddLink,
            label = stringResource(R.string.detail_add_video_link),
            isPrimary = true,
            onClick = onOpenAddVideoLinkDialog,
            modifier = Modifier
                .focusRequester(primaryActionFocusRequester)
                .testTag("add_video_link_button"),
        )

        Text(
            text = stringResource(R.string.detail_video_links_title),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (videoLinks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.detail_video_links_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        } else {
            val columns = if (isWideLayout) 2 else 1
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(0.96f),
            ) {
                videoLinks.chunked(columns).forEach { rowLinks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowLinks.forEach { link ->
                            VideoThumbnailCard(
                                videoLink = link,
                                onOpen = { onOpenVideoLink(link) },
                                onEdit = { onEditVideoLink(link) },
                                onDelete = { onDeleteVideoLink(link) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (columns == 2 && rowLinks.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 9:16 Image & Entry Management Section
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "9:16-Bild & Eintrag",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )

        DetailActionButton(
            icon = Icons.Filled.AddPhotoAlternate,
            label = stringResource(R.string.detail_pick_local_image),
            isPrimary = false,
            onClick = onPickLocalImage,
            modifier = Modifier.testTag("pick_local_image_button"),
        )

        DetailActionButton(
            icon = Icons.Filled.Link,
            label = stringResource(R.string.detail_enter_image_url),
            isPrimary = false,
            onClick = onOpenUrlDialog,
            modifier = Modifier.testTag("enter_image_url_button"),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoThumbnailCard(
    videoLink: VideoLink,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .testTag("open_video_link_${videoLink.id}"),
            shape = CardDefaults.shape(shape = cardShape),
            colors = CardDefaults.colors(
                containerColor = CardSurface,
                focusedContainerColor = CardSurfaceElevated,
            ),
            scale = CardDefaults.scale(focusedScale = 1.04f),
            border = CardDefaults.border(
                border = Border(BorderStroke(1.dp, BorderSubtle), shape = cardShape),
                focusedBorder = Border(BorderStroke(2.5.dp, CinemaGold), shape = cardShape),
            ),
            glow = CardDefaults.glow(
                focusedGlow = Glow(elevationColor = CinemaGoldGlow, elevation = 12.dp),
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 16:9 Video Thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(
                            Brush.verticalGradient(
                                listOf(CardEmptyGradientTop, CardEmptyGradientBottom)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumbModel = remember(videoLink.thumbnailUrl) {
                        ImageDataCodec.toCoilModel(videoLink.thumbnailUrl)
                    }
                    if (thumbModel != null) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(thumbModel)
                                .size(480, 270)
                                .crossfade(true)
                                .build(),
                            contentDescription = videoLink.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                VideoThumbnailPlaceholder(isFocused = isFocused)
                            },
                            error = {
                                VideoThumbnailPlaceholder(isFocused = isFocused)
                            },
                        )
                    } else {
                        VideoThumbnailPlaceholder(isFocused = isFocused)
                    }

                    // Bottom gradient + Play badge
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, ScrimDark.copy(alpha = 0.72f))
                                )
                            )
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomStart),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isFocused) CinemaGold else ObsidianBackground.copy(alpha = 0.8f))
                                    .border(1.dp, CinemaGold, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isFocused) ObsidianBackground else CinemaGold,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (isFocused) {
                                Text(
                                    text = "Video öffnen",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CinemaAmberLight,
                                )
                            }
                        }
                    }
                }

                // Video Title & URL
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = videoLink.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (isFocused) CinemaAmberLight else TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = videoLink.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Edit & Delete Row for the video link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpen,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("launch_video_link_${videoLink.id}"),
                colors = IconButtonDefaults.colors(
                    containerColor = CardSurfaceElevated,
                    contentColor = TextPrimary,
                    focusedContainerColor = CinemaGold,
                    focusedContentColor = ObsidianBackground,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Video öffnen",
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("edit_video_link_${videoLink.id}"),
                colors = IconButtonDefaults.colors(
                    containerColor = CardSurfaceElevated,
                    contentColor = TextPrimary,
                    focusedContainerColor = CinemaGold,
                    focusedContentColor = ObsidianBackground,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Link bearbeiten",
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_video_link_${videoLink.id}"),
                colors = IconButtonDefaults.colors(
                    containerColor = DangerRedContainer,
                    contentColor = DangerRed,
                    focusedContainerColor = DangerRed,
                    focusedContentColor = ObsidianBackground,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Link löschen",
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnailPlaceholder(isFocused: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isFocused) CinemaGoldContainer else CardSurfaceElevated)
                .border(1.5.dp, if (isFocused) CinemaGold else BorderSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (isFocused) CinemaGold else TextSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
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
        modifier = modifier.fillMaxWidth(0.9f),
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
