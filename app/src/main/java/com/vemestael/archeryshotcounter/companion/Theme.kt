package com.vemestael.archeryshotcounter.companion

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Always dark — not tied to system light/dark mode, matching the watch app's own always-dark look. */
private val AppColorScheme = darkColorScheme()

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, content = content)
}
