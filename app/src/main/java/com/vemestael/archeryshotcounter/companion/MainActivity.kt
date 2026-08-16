package com.vemestael.archeryshotcounter.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var sessions by mutableStateOf<List<Session>>(emptyList())
    private var shotsBySession by mutableStateOf<Map<Long, List<Shot>>>(emptyMap())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HistoryScreen(sessions = sessions, shotsBySession = shotsBySession)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val loadedSessions = db.sessionDao().getAll()
            val loadedShots = loadedSessions.associate { it.id to db.shotDao().getBySession(it.id) }
            sessions = loadedSessions
            shotsBySession = loadedShots
        }
    }
}

@Composable
private fun HistoryScreen(sessions: List<Session>, shotsBySession: Map<Long, List<Shot>>) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No synced sessions yet.\nRecord a session on your watch to see it here.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionCard(session, shotsBySession[session.id].orEmpty())
        }
    }
}

@Composable
private fun SessionCard(session: Session, shots: List<Shot>) {
    val dateFormat = remember(session.startTime) { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val avgIntervalSeconds = averageIntervalSeconds(shots)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(dateFormat.format(Date(session.startTime)), style = MaterialTheme.typography.titleMedium)
            Text("${session.shotCount} shots", style = MaterialTheme.typography.bodyMedium)
            if (avgIntervalSeconds != null) {
                Text(
                    "Avg. ${avgIntervalSeconds}s between shots",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (session.shotsPerEndAtStart > 0) {
                Text(
                    "${session.shotsPerEndAtStart} shots per end",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun averageIntervalSeconds(shots: List<Shot>): Int? {
    if (shots.size < 2) return null
    val sorted = shots.sortedBy { it.timestamp }
    val totalMs = sorted.last().timestamp - sorted.first().timestamp
    val intervals = sorted.size - 1
    return ((totalMs / intervals) / 1000.0).roundToInt()
}
