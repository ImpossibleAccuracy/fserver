package com.fserver.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Nocturne is a dark-first system: the design deck is drawn on the dark ground only.
 * The light scheme mirrors it by walking the same ramps from the other end.
 *
 * Dynamic color is deliberately not used — the accent hue carries the product identity
 * (fingerprint confirmation, transfer states) and must not be re-tinted by the wallpaper.
 */
private val DarkColorScheme = darkColorScheme(
    primary = NocturneAccent,
    onPrimary = NocturneAccent900,
    primaryContainer = NocturneAccent800,
    onPrimaryContainer = NocturneAccent100,
    inversePrimary = NocturneAccent700,

    secondary = NocturneAccent2,
    onSecondary = NocturneAccent2_900,
    secondaryContainer = NocturneAccent2_800,
    onSecondaryContainer = NocturneAccent100,

    tertiary = NocturneAccent400,
    onTertiary = NocturneAccent900,
    tertiaryContainer = NocturneAccent2_700,
    onTertiaryContainer = NocturneAccent100,

    background = NocturneBg,
    onBackground = NocturneText,

    surface = NocturneBg,
    onSurface = NocturneText,
    surfaceVariant = NocturneNeutral900,
    onSurfaceVariant = NocturneNeutral500,

    surfaceContainerLowest = NocturneBg,
    surfaceContainerLow = NocturneNeutral900,
    surfaceContainer = NocturneSurface,
    surfaceContainerHigh = NocturneNeutral800,
    surfaceContainerHighest = NocturneNeutral700,

    surfaceTint = NocturneAccent,
    inverseSurface = NocturneNeutral200,
    inverseOnSurface = NocturneNeutral900,

    outline = NocturneNeutral800,
    outlineVariant = NocturneNeutral900,
    scrim = NocturneNeutral900,

    error = NocturneError,
    onError = NocturneErrorDark,
    errorContainer = NocturneErrorContainer,
    onErrorContainer = NocturneNeutral100,
)

private val LightColorScheme = lightColorScheme(
    primary = NocturneAccent700,
    onPrimary = NocturneAccent100,
    primaryContainer = NocturneAccent200,
    onPrimaryContainer = NocturneAccent900,
    inversePrimary = NocturneAccent400,

    secondary = NocturneAccent2_700,
    onSecondary = NocturneAccent100,
    secondaryContainer = NocturneAccent2_400,
    onSecondaryContainer = NocturneAccent2_900,

    tertiary = NocturneAccent600,
    onTertiary = NocturneAccent100,
    tertiaryContainer = NocturneAccent300,
    onTertiaryContainer = NocturneAccent900,

    background = NocturneNeutral100,
    onBackground = NocturneNeutral900,

    surface = NocturneNeutral100,
    onSurface = NocturneNeutral900,
    surfaceVariant = NocturneNeutral200,
    onSurfaceVariant = NocturneNeutral700,

    surfaceContainerLowest = NocturneNeutral100,
    surfaceContainerLow = NocturneNeutral200,
    surfaceContainer = NocturneNeutral200,
    surfaceContainerHigh = NocturneNeutral300,
    surfaceContainerHighest = NocturneNeutral400,

    surfaceTint = NocturneAccent700,
    inverseSurface = NocturneNeutral900,
    inverseOnSurface = NocturneNeutral100,

    outline = NocturneNeutral400,
    outlineVariant = NocturneNeutral300,
    scrim = NocturneNeutral900,

    error = NocturneErrorDark,
    onError = NocturneNeutral100,
    errorContainer = NocturneErrorContainerLight,
    onErrorContainer = NocturneErrorDark,
)

@Composable
fun FServerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = FServerTypography,
        shapes = FServerShapes,
        content = content,
    )
}
