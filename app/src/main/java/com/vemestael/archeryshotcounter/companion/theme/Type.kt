package com.vemestael.archeryshotcounter.companion.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vemestael.archeryshotcounter.R

/** Screen and dialog titles (Material's title/display roles — see [appTypography]). */
val BigShouldersDisplay = FontFamily(
    Font(R.font.big_shoulders_display_bold, FontWeight.Bold)
)

/** Button labels and all other interface text — the app's default font. */
val LibreFranklin = FontFamily(
    Font(R.font.libre_franklin_regular, FontWeight.Normal),
    Font(R.font.libre_franklin_medium, FontWeight.Medium),
    Font(R.font.libre_franklin_semibold, FontWeight.SemiBold),
    Font(R.font.libre_franklin_bold, FontWeight.Bold)
)

/** Numbers: the shot counter, deltas, stepper values, times, magnitudes. */
val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)
