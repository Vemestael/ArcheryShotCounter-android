package com.vemestael.archeryshotcounter.companion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vemestael.archeryshotcounter.R
import com.vemestael.archeryshotcounter.companion.theme.AppCard
import com.vemestael.archeryshotcounter.companion.theme.AppList
import com.vemestael.archeryshotcounter.companion.theme.AppScreen
import com.vemestael.archeryshotcounter.companion.theme.IbmPlexMono
import com.vemestael.archeryshotcounter.companion.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun SessionDetailScreen(session: Session, shots: List<Shot>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val palette = LocalAppPalette.current
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFormat = remember(locale) { SimpleDateFormat("d MMM", locale) }
    val unitAccel = stringResource(R.string.unit_accel)
    val sortedShots = remember(shots) { shots.sortedByDescending { it.timestamp } }
    val totalShots = sortedShots.size

    AppScreen(title = dateFormat.format(Date(session.startTime)), onBack = onBack) { padding ->
        if (sortedShots.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.shots_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textDim
                )
            }
        } else {
            AppList(modifier = Modifier.fillMaxSize().padding(padding), verticalSpacing = 8.dp) {
                itemsIndexed(sortedShots, key = { _, shot -> shot.id }) { index, shot ->
                    val shotNumber = totalShots - index
                    AppCard(contentPadding = PaddingValues(12.dp)) {
                        Text(
                            text = "#$shotNumber",
                            fontFamily = IbmPlexMono,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted
                        )
                        Text(
                            text = timeFormat.format(Date(shot.timestamp)),
                            fontFamily = IbmPlexMono,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text
                        )
                        Text(
                            text = if (shot.magnitude != null) "↑ ${"%.1f".format(shot.magnitude)} $unitAccel" else "—",
                            fontFamily = IbmPlexMono,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (shot.magnitude != null) palette.accent else palette.textMuted
                        )
                    }
                }
            }
        }
    }
}
