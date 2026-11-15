package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val VideothekDarkColorScheme = darkColorScheme(
    primary = CinemaGold,
    onPrimary = ObsidianBackground,
    primaryContainer = CinemaGoldContainer,
    onPrimaryContainer = CinemaAmberLight,
    secondary = SlateAccent,
    onSecondary = ObsidianBackground,
    secondaryContainer = CardSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = CinemaAmberLight,
    onTertiary = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    border = BorderSubtle,
    borderVariant = BorderDashed,
    error = DangerRed,
    onError = ObsidianBackground,
    errorContainer = DangerRedContainer,
    onErrorContainer = DangerRed,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideothekTvTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VideothekDarkColorScheme,
        typography = VideothekTvTypography,
        content = content,
    )
}
