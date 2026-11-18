package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JalanDarkColorScheme = darkColorScheme(
    primary = NavyPrimaryDark,
    onPrimary = OnNavyPrimaryDark,
    primaryContainer = NavyContainerDark,
    onPrimaryContainer = OnNavyContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberSecondaryDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = CyanTertiaryDark,
    onTertiary = OnCyanTertiaryDark,
    tertiaryContainer = CyanContainerDark,
    onTertiaryContainer = OnCyanContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val JalanLightColorScheme = lightColorScheme(
    primary = NavyPrimaryLight,
    onPrimary = OnNavyPrimaryLight,
    primaryContainer = NavyContainerLight,
    onPrimaryContainer = OnNavyContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = OnAmberSecondaryLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = CyanTertiaryLight,
    onTertiary = OnCyanTertiaryLight,
    tertiaryContainer = CyanContainerLight,
    onTertiaryContainer = OnCyanContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

val JalanShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun JalanAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JalanDarkColorScheme else JalanLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JalanTypography,
        shapes = JalanShapes,
        content = content
    )
}
