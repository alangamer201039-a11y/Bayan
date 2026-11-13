package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BayanExtendedColors(
    val glassSurface: Color,
    val glassBorder: Color,
    val mutedText: Color,
    val accentBlue: Color,
    val accentEmber: Color,
    val pillBackground: Color,
    val isDark: Boolean
)

val LocalBayanColors = staticCompositionLocalOf {
    BayanExtendedColors(
        glassSurface = DarkSurface.copy(alpha = 0.85f),
        glassBorder = DarkBorder,
        mutedText = DarkMuted,
        accentBlue = CobaltBlueBright,
        accentEmber = SaffronEmber,
        pillBackground = DarkSurfaceVariant,
        isDark = true
    )
}

private val BayanDarkColorScheme = darkColorScheme(
    primary = CobaltBlueBright,
    onPrimary = Color.White,
    primaryContainer = CobaltBlue.copy(alpha = 0.22f),
    onPrimaryContainer = CobaltBlueSoft,
    secondary = SaffronEmber,
    onSecondary = Color.White,
    secondaryContainer = SaffronEmber.copy(alpha = 0.18f),
    onSecondaryContainer = SaffronEmberSoft,
    tertiary = EmeraldLive,
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkSurface,
    onSurface = DarkForeground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    error = ErrorRed,
    onError = Color.White
)

private val BayanLightColorScheme = lightColorScheme(
    primary = CobaltBlue,
    onPrimary = Color.White,
    primaryContainer = CobaltBlue.copy(alpha = 0.12f),
    onPrimaryContainer = CobaltBlue,
    secondary = SaffronEmber,
    onSecondary = Color.White,
    secondaryContainer = SaffronEmber.copy(alpha = 0.14f),
    onSecondaryContainer = Color(0xFFC2410C),
    tertiary = EmeraldLive,
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightSurface,
    onSurface = LightForeground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun BayanTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BayanDarkColorScheme else BayanLightColorScheme
    val extendedColors = if (darkTheme) {
        BayanExtendedColors(
            glassSurface = DarkSurface.copy(alpha = 0.86f),
            glassBorder = DarkBorder,
            mutedText = DarkMuted,
            accentBlue = CobaltBlueBright,
            accentEmber = SaffronEmber,
            pillBackground = DarkSurfaceVariant,
            isDark = true
        )
    } else {
        BayanExtendedColors(
            glassSurface = LightSurface.copy(alpha = 0.92f),
            glassBorder = LightBorder,
            mutedText = LightMuted,
            accentBlue = CobaltBlue,
            accentEmber = SaffronEmber,
            pillBackground = LightSurfaceVariant,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalBayanColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BayanTypography,
            content = content
        )
    }
}
