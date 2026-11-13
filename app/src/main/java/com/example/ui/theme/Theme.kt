package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PrunelleDarkColorScheme = darkColorScheme(
    primary = PlumPrimaryDark,
    onPrimary = PlumOnPrimaryDark,
    primaryContainer = PlumContainerDark,
    onPrimaryContainer = PlumOnContainerDark,
    secondary = OrchidSecondaryDark,
    onSecondary = OrchidOnSecondaryDark,
    secondaryContainer = OrchidContainerDark,
    onSecondaryContainer = OrchidOnContainerDark,
    tertiary = RoseGoldTertiaryDark,
    onTertiary = RoseGoldOnTertiaryDark,
    tertiaryContainer = RoseGoldContainerDark,
    onTertiaryContainer = RoseGoldOnContainerDark,
    background = ObsidianBackgroundDark,
    onBackground = PearlOnSurfaceDark,
    surface = ObsidianSurfaceDark,
    onSurface = PearlOnSurfaceDark,
    surfaceVariant = ObsidianSurfaceVariantDark,
    onSurfaceVariant = MutedOnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorCrimsonDark
)

private val PrunelleLightColorScheme = lightColorScheme(
    primary = PlumPrimaryLight,
    onPrimary = PlumOnPrimaryLight,
    primaryContainer = PlumContainerLight,
    onPrimaryContainer = PlumOnContainerLight,
    secondary = OrchidSecondaryLight,
    onSecondary = OrchidOnSecondaryLight,
    secondaryContainer = OrchidContainerLight,
    onSecondaryContainer = OrchidOnContainerLight,
    tertiary = RoseGoldTertiaryLight,
    onTertiary = RoseGoldOnTertiaryLight,
    tertiaryContainer = RoseGoldContainerLight,
    onTertiaryContainer = RoseGoldOnContainerLight,
    background = PearlBackgroundLight,
    onBackground = CharcoalOnSurfaceLight,
    surface = PearlSurfaceLight,
    onSurface = CharcoalOnSurfaceLight,
    surfaceVariant = PearlSurfaceVariantLight,
    onSurfaceVariant = MutedOnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorCrimson
)

val PrunelleShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

@Composable
fun PrunelleTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (darkTheme) PrunelleDarkColorScheme else PrunelleLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PrunelleTypography,
        shapes = PrunelleShapes,
        content = content
    )
}
