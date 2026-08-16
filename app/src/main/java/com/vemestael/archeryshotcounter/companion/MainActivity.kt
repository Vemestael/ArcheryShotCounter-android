package com.vemestael.archeryshotcounter.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var sessions by mutableStateOf<List<Session>>(emptyList())
    private var shotsBySession by mutableStateOf<Map<Long, List<Shot>>>(emptyMap())
    private var isSyncing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HistoryScreen(
                        sessions = sessions,
                        shotsBySession = shotsBySession,
                        isSyncing = isSyncing,
                        onSync = ::syncAllFromWatch
                    )
                }
            }
        }
    }

    /** Live delivery while the app is open, in addition to the manifest-declared background service. */
    private val dataListener = DataClient.OnDataChangedListener { events ->
        persistSessionDataEvents(this, events, dbExecutor)
        events.release()
        reload()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(dataListener)
        reload()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(dataListener)
    }

    private fun reload(onComplete: () -> Unit = {}) {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val loadedSessions = db.sessionDao().getAll()
            val loadedShots = loadedSessions.associate { it.id to db.shotDao().getBySession(it.id) }
            runOnUiThread {
                sessions = loadedSessions
                shotsBySession = loadedShots
                onComplete()
            }
        }
    }

    /** Force-pulls every session DataItem currently held by Play Services, not just ones that fired a change event. */
    private fun syncAllFromWatch() {
        isSyncing = true
        Wearable.getDataClient(this).dataItems
            .addOnSuccessListener { buffer ->
                val db = AppDatabase.getInstance(applicationContext)
                dbExecutor.execute {
                    buffer.forEach { item ->
                        if (item.uri.path.orEmpty().startsWith("/session")) {
                            DataMapItem.fromDataItem(item).dataMap.getString("json")?.let { json ->
                                try {
                                    val (session, shots) = parseSessionJson(json)
                                    db.replaceSession(session, shots)
                                } catch (_: Exception) {
                                    // skip malformed item, keep syncing the rest
                                }
                            }
                        }
                    }
                    buffer.release()
                    runOnUiThread { reload { isSyncing = false } }
                }
            }
            .addOnFailureListener { isSyncing = false }
    }
}

@Composable
private fun HistoryScreen(
    sessions: List<Session>,
    shotsBySession: Map<Long, List<Shot>>,
    isSyncing: Boolean,
    onSync: () -> Unit
) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No synced sessions yet.\nRecord a session on your watch to see it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp)
                )
                Button(onClick = onSync, enabled = !isSyncing) {
                    Text(if (isSyncing) "Syncing…" else "Sync data")
                }
            }
        }
        return
    }

    PullToRefreshHistoryList(sessions, shotsBySession, isSyncing, onSync)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshHistoryList(
    sessions: List<Session>,
    shotsBySession: Map<Long, List<Shot>>,
    isSyncing: Boolean,
    onSync: () -> Unit
) {
    PullToRefreshBox(isRefreshing = isSyncing, onRefresh = onSync, modifier = Modifier.fillMaxSize()) {
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
