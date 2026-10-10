package com.vemestael.archeryshotcounter.companion.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** Not tied to system light/dark mode — the chosen [AppPalette] alone decides, as on the watch. */
@Composable
fun ArcheryShotCounterTheme(
    palette: AppPalette = BrassPalette,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = appTypography(),
            content = content
        )
    }
}

/** Material's own phone type scale, with the two fonts from the design system swapped in:
 * title/display/headline roles (top bar, dialog and section titles) get Big Shoulders, everything
 * else — buttons, labels, body — gets Libre Franklin. Sizes, weights and line-heights are
 * untouched, same as the watch's [appTypography]. */
private fun appTypography(): Typography {
    val base = Typography()
    fun TextStyle.with(family: FontFamily) = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.with(BigShouldersDisplay),
        displayMedium = base.displayMedium.with(BigShouldersDisplay),
        displaySmall = base.displaySmall.with(BigShouldersDisplay),
        headlineLarge = base.headlineLarge.with(BigShouldersDisplay),
        headlineMedium = base.headlineMedium.with(BigShouldersDisplay),
        headlineSmall = base.headlineSmall.with(BigShouldersDisplay),
        titleLarge = base.titleLarge.with(BigShouldersDisplay),
        titleMedium = base.titleMedium.with(BigShouldersDisplay),
        titleSmall = base.titleSmall.with(BigShouldersDisplay),
        bodyLarge = base.bodyLarge.with(LibreFranklin),
        bodyMedium = base.bodyMedium.with(LibreFranklin),
        bodySmall = base.bodySmall.with(LibreFranklin),
        labelLarge = base.labelLarge.with(LibreFranklin),
        labelMedium = base.labelMedium.with(LibreFranklin),
        labelSmall = base.labelSmall.with(LibreFranklin)
    )
}

/** Maps the palette onto Material roles so stock components (Scaffold's background, the top bar,
 * DatePicker, PullToRefresh indicator, etc.) pick it up automatically, without every screen
 * reaching for [LocalAppPalette] directly. Parchment is the one light palette, so the base scheme
 * is picked by the background's luminance. */
private fun AppPalette.toColorScheme(): ColorScheme {
    val base = if (bg.luminance() > 0.5f) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        inversePrimary = accent,
        secondary = textDim,
        onSecondary = bg,
        secondaryContainer = bgElev2,
        onSecondaryContainer = text,
        tertiary = active,
        onTertiary = bg,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = bgElev2,
        onSurfaceVariant = textDim,
        surfaceTint = accent,
        inverseSurface = text,
        inverseOnSurface = bg,
        surfaceBright = bgElev2,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = bgElev,
        surfaceContainer = bgElev,
        surfaceContainerHigh = bgElev2,
        surfaceContainerHighest = bgElev2,
        outline = line,
        outlineVariant = line,
        error = pause,
        onError = onPause,
        errorContainer = pause,
        onErrorContainer = onPause
    )
}
