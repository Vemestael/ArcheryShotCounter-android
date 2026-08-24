package com.vemestael.archeryshotcounter.companion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vemestael.archeryshotcounter.R
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(session: Session, shots: List<Shot>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFormat = remember(locale) { SimpleDateFormat("d MMM", locale) }
    val unitAccel = stringResource(R.string.unit_accel)
    val sortedShots = remember(shots) { shots.sortedByDescending { it.timestamp } }
    val totalShots = sortedShots.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateFormat.format(Date(session.startTime))) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
                }
            )
        }
    ) { padding ->
        if (sortedShots.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.shots_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(sortedShots, key = { _, shot -> shot.id }) { index, shot ->
                    val shotNumber = totalShots - index
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "#$shotNumber",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = timeFormat.format(Date(shot.timestamp)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (shot.magnitude != null) "↑ ${"%.1f".format(shot.magnitude)} $unitAccel" else "—",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (shot.magnitude != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
