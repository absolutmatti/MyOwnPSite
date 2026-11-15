package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.Performer
import com.example.ui.theme.BorderDashed
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardEmptyGradientBottom
import com.example.ui.theme.CardEmptyGradientTop
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CinemaAmberLight
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.CinemaGoldContainer
import com.example.ui.theme.CinemaGoldGlow
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ScrimDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PerformerTileCard(
    performer: Performer,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocused()
                }
            }
            .testTag("performer_tile_${performer.id}"),
        shape = CardDefaults.shape(shape = cardShape),
        colors = CardDefaults.colors(
            containerColor = CardSurface,
            focusedContainerColor = CardSurfaceElevated,
        ),
        scale = CardDefaults.scale(
            focusedScale = 1.06f,
            pressedScale = 0.98f,
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.7f)),
                shape = cardShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, CinemaGold),
                shape = cardShape,
            ),
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = CinemaGoldGlow,
                elevation = 16.dp,
            ),
        ),
    ) {
        PerformerPortraitFrame(
            performer = performer,
            isFocused = isFocused,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun PerformerPortraitFrame(
    performer: Performer,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    showFocusHint: Boolean = true,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(CardEmptyGradientTop, CardEmptyGradientBottom),
            )
        )
    ) {
        if (performer.hasImage) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(performer.imageUri)
                    .size(360, 640)
                    .crossfade(true)
                    .build(),
                contentDescription = performer.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    EmptyPortraitPlaceholder(
                        initials = performer.initials,
                        isFocused = isFocused,
                        showFocusHint = false,
                    )
                },
                error = {
                    EmptyPortraitPlaceholder(
                        initials = performer.initials,
                        isFocused = isFocused,
                        showFocusHint = showFocusHint,
                    )
                },
            )
        } else {
            EmptyPortraitPlaceholder(
                initials = performer.initials,
                isFocused = isFocused,
                showFocusHint = showFocusHint,
            )
        }

        // Top badges: aspect ratio tag (when empty) & video links counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!performer.hasImage) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isFocused) CinemaGoldContainer.copy(alpha = 0.9f)
                            else ObsidianBackground.copy(alpha = 0.65f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isFocused) CinemaGold.copy(alpha = 0.7f) else BorderSubtle,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "9:16",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) CinemaAmberLight else TextMuted,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            if (performer.videoCount > 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isFocused) CinemaGoldContainer.copy(alpha = 0.92f)
                            else ObsidianBackground.copy(alpha = 0.78f)
                        )
                        .border(
                            width = 1.dp,
                            color = CinemaGold.copy(alpha = if (isFocused) 0.85f else 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = CinemaGold,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = performer.videoCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CinemaAmberLight,
                    )
                }
            }
        }

        // Bottom scrim & performer name
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ScrimDark.copy(alpha = 0.85f),
                            ObsidianBackground.copy(alpha = 0.96f),
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = performer.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = if (isFocused) CinemaAmberLight else TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!performer.hasImage && showFocusHint) {
                    AnimatedVisibility(
                        visible = isFocused,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = stringResource(R.string.empty_tile_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPortraitPlaceholder(
    initials: String,
    isFocused: Boolean,
    showFocusHint: Boolean,
) {
    val strokeColor = if (isFocused) CinemaGold.copy(alpha = 0.55f) else BorderDashed.copy(alpha = 0.75f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .drawBehind {
                drawRoundRect(
                    color = strokeColor,
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 26.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFocused) CinemaGoldContainer.copy(alpha = 0.8f)
                        else CardSurfaceElevated.copy(alpha = 0.85f)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isFocused) CinemaGold else BorderSubtle,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (isFocused) CinemaAmberLight else TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (showFocusHint && isFocused) {
                Spacer(modifier = Modifier.height(10.dp))
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    tint = CinemaGold.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
