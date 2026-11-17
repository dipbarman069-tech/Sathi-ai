package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SaathiDarkColorScheme =
  darkColorScheme(
    primary = SonicCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = SonicCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = WarmAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = WarmAmberDim,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldSafe,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    outlineVariant = SlateHover,
    error = CrimsonAlert,
    onError = TextPrimary,
    errorContainer = CrimsonSurface,
    onErrorContainer = TextPrimary,
  )

@Composable
fun SaathiTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = SaathiDarkColorScheme,
    typography = SaathiTypography,
    content = content,
  )
}
