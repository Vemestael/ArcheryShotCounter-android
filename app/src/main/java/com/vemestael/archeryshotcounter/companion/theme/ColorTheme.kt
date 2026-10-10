package com.vemestael.archeryshotcounter.companion.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vemestael.archeryshotcounter.R

/**
 * Every colour the UI uses, named by role instead of hex, so a screen never hard-codes a shade.
 *
 * [accent] is the single bright highlight: the shot counter, the primary button, selected state.
 * [active] marks an in-progress state (detecting), [pause] marks pause and destructive actions.
 */
data class AppPalette(
    val bg: Color,
    val bgElev: Color,
    val bgElev2: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val active: Color,
    val pause: Color,
    val onPause: Color,
    /** Text color for neutral (non-accent, non-destructive) buttons. Defaults to [textDim]. */
    val buttonTextDim: Color = textDim
)

/** "Латунь": warm sand and brass on coal. The app's original, default palette. */
val BrassPalette = AppPalette(
    bg = Color(0xFF15120F),
    bgElev = Color(0xFF201B15),
    bgElev2 = Color(0xFF262019),
    line = Color(0xFF3A3125),
    text = Color(0xFFF3EAD9),
    textDim = Color(0xFFA89C8A),
    textMuted = Color(0xFF7A6F60),
    accent = Color(0xFFC9A36A),
    onAccent = Color(0xFF1A1510),
    active = Color(0xFFA3B17E),
    pause = Color(0xFFC0603F),
    onPause = Color(0xFFF3EAD9)
)

/** "Лес": deep green, sand brass and hunting orange. */
val ForestPalette = AppPalette(
    bg = Color(0xFF161D12),
    bgElev = Color(0xFF1C2416),
    bgElev2 = Color(0xFF22291A),
    line = Color(0xFF2C3522),
    text = Color(0xFFECE7DA),
    textDim = Color(0xFF9AA08F),
    textMuted = Color(0xFF6D7266),
    accent = Color(0xFFC9A36A),
    onAccent = Color(0xFF1A1510),
    active = Color(0xFF9FB17E),
    pause = Color(0xFFD9631F),
    onPause = Color(0xFF1A1510)
)

/** "Мишень": target-ring colors — gold, red, blue on near-black. */
val TargetPalette = AppPalette(
    bg = Color(0xFF141414),
    bgElev = Color(0xFF1A1A1A),
    bgElev2 = Color(0xFF201F1E),
    line = Color(0xFF2A2A2A),
    text = Color(0xFFF5F5F0),
    textDim = Color(0xFF8A8A85),
    textMuted = Color(0xFF626260),
    accent = Color(0xFFE8B923),
    onAccent = Color(0xFF1A1510),
    active = Color(0xFF5C9BD1),
    pause = Color(0xFFC23B3B),
    onPause = Color(0xFFF5F5F0),
    buttonTextDim = Color(0xFFF5F5F0)
)

/** "Сталь": cold graphite + ice blue, tactical compound-bow look. */
val SteelPalette = AppPalette(
    bg = Color(0xFF11161B),
    bgElev = Color(0xFF161B20),
    bgElev2 = Color(0xFF1C2024),
    line = Color(0xFF263038),
    text = Color(0xFFE9EEF2),
    textDim = Color(0xFF8D9AA3),
    textMuted = Color(0xFF646D74),
    accent = Color(0xFF6FB3D8),
    onAccent = Color(0xFF0C0F12),
    active = Color(0xFF9FB0A9),
    pause = Color(0xFFD9772E),
    onPause = Color(0xFF1A140C),
    buttonTextDim = Color(0xFFE9EEF2)
)

/** "Неон": black + neon lime, modern compound-bow style. */
val NeonPalette = AppPalette(
    bg = Color(0xFF141414),
    bgElev = Color(0xFF171717),
    bgElev2 = Color(0xFF1D1C1B),
    line = Color(0xFF2A2A2A),
    text = Color(0xFFF0F0EC),
    textDim = Color(0xFF8F8F87),
    textMuted = Color(0xFF666660),
    accent = Color(0xFFC6F135),
    onAccent = Color(0xFF121206),
    active = Color(0xFFA9B89B),
    pause = Color(0xFFE2423A),
    onPause = Color(0xFFF0F0EC),
    buttonTextDim = Color(0xFFF0F0EC)
)

/** "Пергамент": the one light palette — parchment cream with deep red accent. */
val ParchmentPalette = AppPalette(
    bg = Color(0xFFECE2C9),
    bgElev = Color(0xFFE2D4B0),
    bgElev2 = Color(0xFFD8C697),
    line = Color(0xFFC9B98C),
    text = Color(0xFF2B2015),
    textDim = Color(0xFF6B5C43),
    textMuted = Color(0xFF92846B),
    accent = Color(0xFF7A2B23),
    onAccent = Color(0xFFECE2C9),
    active = Color(0xFF4F5A3A),
    pause = Color(0xFF7A2B23),
    onPause = Color(0xFFECE2C9)
)

/** The themes selectable in Settings → Appearance. */
enum class PaletteChoice(@param:StringRes val labelRes: Int, val palette: AppPalette) {
    BRASS(R.string.theme_brass, BrassPalette),
    FOREST(R.string.theme_forest, ForestPalette),
    TARGET(R.string.theme_target, TargetPalette),
    STEEL(R.string.theme_steel, SteelPalette),
    NEON(R.string.theme_neon, NeonPalette),
    PARCHMENT(R.string.theme_parchment, ParchmentPalette)
}

val LocalAppPalette = staticCompositionLocalOf { BrassPalette }
