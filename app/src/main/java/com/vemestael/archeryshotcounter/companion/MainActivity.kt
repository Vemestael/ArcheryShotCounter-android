package com.vemestael.archeryshotcounter.companion

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val IMPORT_JSON_FILENAME = "archery-import.json"
private const val IMPORT_CSV_FILENAME = "archery-import.csv"

class MainActivity : ComponentActivity() {

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var sessions by mutableStateOf<List<Session>>(emptyList())
    private var shotsBySession by mutableStateOf<Map<Long, List<Shot>>>(emptyMap())
    private var isSyncing by mutableStateOf(false)
    private var editingSession by mutableStateOf<Session?>(null)
    private var showClearDataConfirm by mutableStateOf(false)

    private var dataStatus by mutableStateOf<String?>(null)
    private val dataStatusHandler = Handler(Looper.getMainLooper())
    private val dataStatusHideRunnable = Runnable { dataStatus = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(
                        sessions = sessions,
                        shotsBySession = shotsBySession,
                        isSyncing = isSyncing,
                        dataStatus = dataStatus,
                        onSync = ::syncData,
                        onExportJson = ::exportJson,
                        onImportJson = ::importJson,
                        onExportCsv = ::exportCsv,
                        onImportCsv = ::importCsv,
                        onEditSession = { editingSession = it },
                        onClearData = { showClearDataConfirm = true }
                    )
                    editingSession?.let { session ->
                        EditSessionDialog(
                            session = session,
                            onSave = { saveSessionEdit(it); editingSession = null },
                            onDelete = { deleteSession(it); editingSession = null },
                            onDismiss = { editingSession = null }
                        )
                    }
                    if (showClearDataConfirm) {
                        ClearDataConfirmDialog(
                            onConfirm = { confirmClearData(); showClearDataConfirm = false },
                            onCancel = { showClearDataConfirm = false }
                        )
                    }
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

    private fun showDataStatus(message: String) {
        dataStatus = message
        dataStatusHandler.removeCallbacks(dataStatusHideRunnable)
        dataStatusHandler.postDelayed(dataStatusHideRunnable, 6000)
    }

    /** Pushes a DataItem to the watch app. Call from a background thread. */
    private fun syncSessionToWatch(session: Session, shots: List<Shot>) {
        val request = PutDataMapRequest.create("/session/${session.id}").apply {
            dataMap.putString("json", buildSessionJson(session, shots))
            dataMap.putLong("syncedAt", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
    }

    /** Full bidirectional reconcile: push every local session (tombstones included, so deletes
     * propagate) and merge in whatever the watch's DataItems currently hold. */
    private fun syncData() {
        isSyncing = true
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val allSessions = db.sessionDao().getAllIncludingDeleted()
            val shotsBySessionAll = db.shotDao().getAll().groupBy { it.sessionId }
            allSessions.forEach { session -> syncSessionToWatch(session, shotsBySessionAll[session.id].orEmpty()) }
        }
        Wearable.getDataClient(this).dataItems
            .addOnSuccessListener { buffer ->
                dbExecutor.execute {
                    buffer.forEach { item ->
                        if (item.uri.path.orEmpty().startsWith("/session")) {
                            DataMapItem.fromDataItem(item).dataMap.getString("json")?.let { json ->
                                try {
                                    val (session, shots) = parseSessionJson(json)
                                    db.mergeIncomingSession(session, shots)
                                } catch (_: Exception) {
                                    // skip malformed item, keep reconciling the rest
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

    private fun saveSessionEdit(edited: Session) {
        val updated = edited.copy(lastModified = System.currentTimeMillis())
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            db.sessionDao().insertOrUpdate(updated)
            syncSessionToWatch(updated, db.shotDao().getBySession(updated.id))
            runOnUiThread { reload() }
        }
    }

    /** Tombstones rather than hard-deletes, so the deletion propagates to the watch instead of
     * the watch's still-existing copy getting re-synced back down and resurrecting it. */
    private fun deleteSession(session: Session) {
        val now = System.currentTimeMillis()
        val tombstone = session.copy(deletedAt = now, lastModified = now)
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            db.sessionDao().insertOrUpdate(tombstone)
            db.shotDao().deleteAllForSession(session.id)
            syncSessionToWatch(tombstone, emptyList())
            runOnUiThread { reload() }
        }
    }

    /** Local wipe only — deliberately does not push a tombstone for every session, since that
     * would delete everything on the watch too on next sync. Sync afterwards to restore from
     * the watch if it still has the data. */
    private fun confirmClearData() {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            db.clearAllLocalData()
            runOnUiThread {
                sessions = emptyList()
                shotsBySession = emptyMap()
                showDataStatus("Cleared")
            }
        }
    }

    private fun exportJson() {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val path = try {
                val allSessions = db.sessionDao().getAllIncludingDeleted()
                val shotsAll = db.shotDao().getAll().groupBy { it.sessionId }
                val json = buildExportJson(allSessions, shotsAll)
                val dir = getExternalFilesDir(null) ?: throw IOException("no external files dir")
                val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                val file = File(dir, "archery-export-$stamp.json")
                file.writeText(json)
                file.absolutePath
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                showDataStatus(if (path != null) "Exported to $path" else "Export failed")
            }
        }
    }

    private fun exportCsv() {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val path = try {
                val allSessions = db.sessionDao().getAllIncludingDeleted()
                val shotsAll = db.shotDao().getAll().groupBy { it.sessionId }
                val csv = buildExportCsv(allSessions, shotsAll)
                val dir = getExternalFilesDir(null) ?: throw IOException("no external files dir")
                val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                val file = File(dir, "archery-export-$stamp.csv")
                file.writeText(csv)
                file.absolutePath
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                showDataStatus(if (path != null) "Exported to $path" else "Export failed")
            }
        }
    }

    private fun importJson() = importFrom(IMPORT_JSON_FILENAME) { parseImportJson(it) }

    private fun importCsv() = importFrom(IMPORT_CSV_FILENAME) { parseImportCsv(it) }

    private fun importFrom(filename: String, parse: (String) -> List<ImportedSession>) {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val dir = getExternalFilesDir(null)
            val file = dir?.let { File(it, filename) }
            if (file == null || !file.exists()) {
                val hint = if (dir != null) File(dir, filename).absolutePath else filename
                runOnUiThread { showDataStatus("No file found. Place a file at $hint") }
                return@execute
            }
            val success = try {
                val imported = parse(file.readText())
                imported.forEach { (session, shots) ->
                    db.mergeIncomingSession(session, shots)
                    syncSessionToWatch(session, shots)
                }
                imported.size
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                showDataStatus(if (success != null) "Imported $success sessions" else "Import failed")
                reload()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dataStatusHandler.removeCallbacks(dataStatusHideRunnable)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    sessions: List<Session>,
    shotsBySession: Map<Long, List<Shot>>,
    isSyncing: Boolean,
    dataStatus: String?,
    onSync: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onEditSession: (Session) -> Unit,
    onClearData: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archery Shot Counter") },
                actions = {
                    TextButton(onClick = { showMenu = true }) { Text("⋮") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Export JSON") }, onClick = { showMenu = false; onExportJson() })
                        DropdownMenuItem(text = { Text("Import JSON") }, onClick = { showMenu = false; onImportJson() })
                        DropdownMenuItem(text = { Text("Export CSV") }, onClick = { showMenu = false; onExportCsv() })
                        DropdownMenuItem(text = { Text("Import CSV") }, onClick = { showMenu = false; onImportCsv() })
                        DropdownMenuItem(text = { Text("Clear data") }, onClick = { showMenu = false; onClearData() })
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (dataStatus != null) {
                Text(
                    text = dataStatus,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                HistoryScreen(sessions, shotsBySession, isSyncing, onSync, onEditSession)
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    sessions: List<Session>,
    shotsBySession: Map<Long, List<Shot>>,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onEditSession: (Session) -> Unit
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

    PullToRefreshHistoryList(sessions, shotsBySession, isSyncing, onSync, onEditSession)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshHistoryList(
    sessions: List<Session>,
    shotsBySession: Map<Long, List<Shot>>,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onEditSession: (Session) -> Unit
) {
    PullToRefreshBox(isRefreshing = isSyncing, onRefresh = onSync, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(session, shotsBySession[session.id].orEmpty(), onClick = { onEditSession(session) })
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session, shots: List<Shot>, onClick: () -> Unit) {
    val dateFormat = remember(session.startTime) { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val avgIntervalSeconds = averageIntervalSeconds(shots)

    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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

@Composable
private fun EditSessionDialog(
    session: Session,
    onSave: (Session) -> Unit,
    onDelete: (Session) -> Unit,
    onDismiss: () -> Unit
) {
    var count by remember { mutableIntStateOf(session.shotCount) }
    var durationMinutes by remember {
        mutableIntStateOf((((session.lastShotTime - session.startTime) / 60_000L).toInt()).coerceAtLeast(0))
    }
    var shotsPerEnd by remember { mutableIntStateOf(session.shotsPerEndAtStart) }
    val dateFormat = remember(session.startTime) { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dateFormat.format(Date(session.startTime))) },
        text = {
            Column {
                StepperRow("Shots", count, onDec = { if (count > 0) count-- }, onInc = { count++ })
                StepperRow("Duration, min", durationMinutes, onDec = { if (durationMinutes > 0) durationMinutes-- }, onInc = { durationMinutes++ })
                StepperRow("Shots per end", shotsPerEnd, onDec = { if (shotsPerEnd > 0) shotsPerEnd-- }, onInc = { shotsPerEnd++ }, zeroLabel = "Off")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    session.copy(
                        shotCount = count,
                        lastShotTime = session.startTime + durationMinutes * 60_000L,
                        shotsPerEndAtStart = shotsPerEnd
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(session) }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ClearDataConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Clear all data?") },
        text = { Text("Deletes every session and shot stored on this phone. This does not affect the watch — sync afterwards to restore from there. This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun StepperRow(label: String, value: Int, onDec: () -> Unit, onInc: () -> Unit, zeroLabel: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onDec) { Text("−") }
        Text(
            if (value == 0 && zeroLabel != null) zeroLabel else "$value",
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        OutlinedButton(onClick = onInc) { Text("+") }
    }
}

private fun averageIntervalSeconds(shots: List<Shot>): Int? {
    if (shots.size < 2) return null
    val sorted = shots.sortedBy { it.timestamp }
    val totalMs = sorted.last().timestamp - sorted.first().timestamp
    val intervals = sorted.size - 1
    return ((totalMs / intervals) / 1000.0).roundToInt()
}
