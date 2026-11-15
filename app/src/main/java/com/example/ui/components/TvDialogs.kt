package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.R
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ScrimDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var textValue by remember(initialValue) { mutableStateOf(initialValue) }
    var inputFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .border(1.5.dp, BorderSubtle, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )

            DialogTextInputField(
                label = label,
                value = textValue,
                onValueChange = { textValue = it },
                isFocused = inputFocused,
                onFocusChange = { inputFocused = it },
                modifier = Modifier
                    .focusRequester(inputFocusRequester)
                    .testTag("dialog_text_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dialog_cancel_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = CardSurfaceElevated,
                        contentColor = TextPrimary,
                        focusedContainerColor = CinemaGold,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(text = stringResource(R.string.dialog_cancel))
                }

                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                Button(
                    onClick = {
                        val trimmed = textValue.trim()
                        if (trimmed.isNotEmpty()) {
                            onConfirm(trimmed)
                        }
                    },
                    modifier = Modifier.testTag("dialog_confirm_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaGold,
                        contentColor = ObsidianBackground,
                        focusedContainerColor = TextPrimary,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(text = confirmText)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoLinkDialog(
    title: String,
    initialTitle: String,
    initialUrl: String,
    initialThumbnailUrl: String,
    confirmText: String,
    onConfirm: (title: String, url: String, thumbnailUrl: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var titleValue by remember(initialTitle) { mutableStateOf(initialTitle) }
    var urlValue by remember(initialUrl) { mutableStateOf(initialUrl) }
    var thumbnailValue by remember(initialThumbnailUrl) { mutableStateOf(initialThumbnailUrl) }
    var titleFocused by remember { mutableStateOf(false) }
    var urlFocused by remember { mutableStateOf(false) }
    var thumbnailFocused by remember { mutableStateOf(false) }
    val urlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        urlFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .border(1.5.dp, BorderSubtle, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )

            DialogTextInputField(
                label = stringResource(R.string.dialog_video_url_label),
                value = urlValue,
                onValueChange = { urlValue = it },
                isFocused = urlFocused,
                onFocusChange = { urlFocused = it },
                modifier = Modifier
                    .focusRequester(urlFocusRequester)
                    .testTag("dialog_video_url_input"),
            )

            DialogTextInputField(
                label = stringResource(R.string.dialog_video_title_label),
                value = titleValue,
                onValueChange = { titleValue = it },
                isFocused = titleFocused,
                onFocusChange = { titleFocused = it },
                modifier = Modifier.testTag("dialog_video_title_input"),
            )

            DialogTextInputField(
                label = stringResource(R.string.dialog_video_thumbnail_label),
                value = thumbnailValue,
                onValueChange = { thumbnailValue = it },
                isFocused = thumbnailFocused,
                onFocusChange = { thumbnailFocused = it },
                modifier = Modifier.testTag("dialog_video_thumbnail_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dialog_cancel_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = CardSurfaceElevated,
                        contentColor = TextPrimary,
                        focusedContainerColor = CinemaGold,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(text = stringResource(R.string.dialog_cancel))
                }

                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                Button(
                    onClick = {
                        val trimmedUrl = urlValue.trim()
                        if (trimmedUrl.isNotEmpty()) {
                            onConfirm(
                                titleValue.trim(),
                                trimmedUrl,
                                thumbnailValue.trim().takeIf { it.isNotEmpty() },
                            )
                        }
                    },
                    modifier = Modifier.testTag("dialog_confirm_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaGold,
                        contentColor = ObsidianBackground,
                        focusedContainerColor = TextPrimary,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(text = confirmText)
                }
            }
        }
    }
}

@Composable
private fun DialogTextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardSurfaceElevated)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) CinemaGold else BorderSubtle,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                cursorBrush = SolidColor(CinemaGold),
                modifier = modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChange(it.isFocused) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvConfirmDeleteDialog(
    performerName: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .border(1.5.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.dialog_delete_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )

            Text(
                text = "Möchtest du \"$performerName\" wirklich aus deiner Videothek entfernen?",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .focusRequester(cancelFocusRequester)
                        .testTag("delete_cancel_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = CardSurfaceElevated,
                        contentColor = TextPrimary,
                        focusedContainerColor = CinemaGold,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(text = stringResource(R.string.dialog_cancel))
                }

                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                Button(
                    onClick = onConfirmDelete,
                    modifier = Modifier.testTag("delete_confirm_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = DangerRedContainer,
                        contentColor = DangerRed,
                        focusedContainerColor = DangerRed,
                        focusedContentColor = ObsidianBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(text = stringResource(R.string.dialog_delete_confirm))
                }
            }
        }
    }
}
