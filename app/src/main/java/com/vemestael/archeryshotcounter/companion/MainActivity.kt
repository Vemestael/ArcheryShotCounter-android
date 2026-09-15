package com.vemestael.archeryshotcounter.companion

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import com.vemestael.archeryshotcounter.R
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

enum class DataFormat { JSON, CSV }

private const val PREFS_NAME = "settings"
private const val KEY_LANGUAGE = "language"

class MainActivity : ComponentActivity() {

    private var currentLanguage = AppLanguage.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        val code = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code
        currentLanguage = AppLanguage.entries.find { it.code == code } ?: AppLanguage.SYSTEM
        if (code == AppLanguage.SYSTEM.code) {
            super.attachBaseContext(newBase)
        } else {
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(Locale.forLanguageTag(code))
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    private fun changeLanguage(lang: AppLanguage) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putString(KEY_LANGUAGE, lang.code) }
        recreate()
    }

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var sessions by mutableStateOf<List<Session>>(emptyList())
    private var shotsBySession by mutableStateOf<Map<Long, List<Shot>>>(emptyMap())
    private var isSyncing by mutableStateOf(false)
    private var editingSession by mutableStateOf<Session?>(null)
    private var detailSession by mutableStateOf<Session?>(null)
    private var activeSessionId by mutableStateOf<Long?>(null)
    private var showClearDataConfirm by mutableStateOf(false)
    private var showExportDialog by mutableStateOf(false)
    private var showImportDialog by mutableStateOf(false)
    private var showLanguagePicker by mutableStateOf(false)

    private var dataStatus by mutableStateOf<String?>(null)
    private val dataStatusHandler = Handler(Looper.getMainLooper())
    private val dataStatusHideRunnable = Runnable { dataStatus = null }

    private var pendingImportFormat = DataFormat.JSON

    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportTo(it, DataFormat.JSON) }
    }
    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { exportTo(it, DataFormat.CSV) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFrom(it, pendingImportFormat) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val detail = detailSession
                    if (detail != null) {
                        SessionDetailScreen(
                            session = detail,
                            shots = shotsBySession[detail.id].orEmpty(),
                            onBack = { detailSession = null }
                        )
                        return@Surface
                    }
                    AppScreen(
                        sessions = sessions,
                        activeSessionId = activeSessionId,
                        isSyncing = isSyncing,
                        dataStatus = dataStatus,
                        onSync = ::syncData,
                        onOpenExportDialog = { showExportDialog = true },
                        onOpenImportDialog = { showImportDialog = true },
                        onOpenLanguagePicker = { showLanguagePicker = true },
                        onEditSession = { editingSession = it },
                        onClearData = { showClearDataConfirm = true }
                    )
                    editingSession?.let { session ->
                        EditSessionDialog(
                            session = session,
                            onShowDetail = { detailSession = session; editingSession = null },
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
                    if (showExportDialog) {
                        ExportDialog(
                            onDismiss = { showExportDialog = false },
                            onExport = { format -> requestExport(format); showExportDialog = false }
                        )
                    }
                    if (showImportDialog) {
                        ImportDialog(
                            onDismiss = { showImportDialog = false },
                            onImport = { format -> requestImport(format); showImportDialog = false }
                        )
                    }
                    if (showLanguagePicker) {
                        LanguagePickerDialog(
                            currentLanguage = currentLanguage,
                            onSelect = { changeLanguage(it) },
                            onDismiss = { showLanguagePicker = false }
                        )
                    }
                }
            }
        }
    }

    /** Live delivery while the app is open, in addition to the manifest-declared background service. */
    private val dataListener = DataClient.OnDataChangedListener { events ->
        persistSessionDataEvents(this, events, dbExecutor)
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/activeSession") {
                val id = DataMapItem.fromDataItem(event.dataItem).dataMap.getLong("sessionId", -1L)
                activeSessionId = if (id >= 0) id else null
            }
        }
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
                    var foundActiveId: Long? = activeSessionId
                    buffer.forEach { item ->
                        when {
                            item.uri.path.orEmpty().startsWith("/session") -> {
                                DataMapItem.fromDataItem(item).dataMap.getString("json")?.let { json ->
                                    try {
                                        val (session, shots) = parseSessionJson(json)
                                        db.mergeIncomingSession(session, shots)
                                    } catch (_: Exception) {
                                        // skip malformed item, keep reconciling the rest
                                    }
                                }
                            }
                            item.uri.path == "/activeSession" -> {
                                val id = DataMapItem.fromDataItem(item).dataMap.getLong("sessionId", -1L)
                                foundActiveId = if (id >= 0) id else null
                            }
                        }
                    }
                    buffer.release()
                    runOnUiThread {
                        activeSessionId = foundActiveId
                        reload { isSyncing = false }
                    }
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
                showDataStatus(getString(R.string.clear_data_success))
            }
        }
    }

    private fun requestExport(format: DataFormat) {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        when (format) {
            DataFormat.JSON -> exportJsonLauncher.launch("archery-export-$stamp.json")
            DataFormat.CSV -> exportCsvLauncher.launch("archery-export-$stamp.csv")
        }
    }

    private fun requestImport(format: DataFormat) {
        pendingImportFormat = format
        val mimeTypes = when (format) {
            DataFormat.JSON -> arrayOf("application/json", "text/*", "*/*")
            DataFormat.CSV -> arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
        }
        importLauncher.launch(mimeTypes)
    }

    private fun exportTo(uri: Uri, format: DataFormat) {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val ok = try {
                val allSessions = db.sessionDao().getAllIncludingDeleted()
                val shotsAll = db.shotDao().getAll().groupBy { it.sessionId }
                val content = when (format) {
                    DataFormat.JSON -> buildExportJson(allSessions, shotsAll)
                    DataFormat.CSV -> buildExportCsv(allSessions, shotsAll)
                }
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    ?: throw IOException("no output stream")
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                showDataStatus(getString(if (ok) R.string.export_success else R.string.export_failed))
            }
        }
    }

    private fun importFrom(uri: Uri, format: DataFormat) {
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            val count = try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IOException("no input stream")
                val imported = when (format) {
                    DataFormat.JSON -> parseImportJson(text)
                    DataFormat.CSV -> parseImportCsv(text)
                }
                imported.forEach { (session, shots) ->
                    db.mergeIncomingSession(session, shots)
                    syncSessionToWatch(session, shots)
                }
                imported.size
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                showDataStatus(
                    if (count != null) getString(R.string.import_success, count) else getString(R.string.import_failed)
                )
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
    activeSessionId: Long?,
    isSyncing: Boolean,
    dataStatus: String?,
    onSync: () -> Unit,
    onOpenExportDialog: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onEditSession: (Session) -> Unit,
    onClearData: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { showMenu = true }) { Text("⋮", fontSize = 28.sp) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_button)) }, onClick = { showMenu = false; onOpenExportDialog() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.import_button)) }, onClick = { showMenu = false; onOpenImportDialog() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.lang_menu)) }, onClick = { showMenu = false; onOpenLanguagePicker() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_data_menu)) }, onClick = { showMenu = false; onClearData() })
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
                HistoryScreen(sessions, activeSessionId, isSyncing, onSync, onEditSession)
            }
        }
    }
}

private sealed class HistoryListItem {
    data class SessionItem(val session: Session) : HistoryListItem()
    data class YearLabel(val year: Int) : HistoryListItem()
}

private fun buildHistoryItems(sessions: List<Session>): List<HistoryListItem> {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val result = mutableListOf<HistoryListItem>()
    var lastYear: Int? = null
    sessions.forEach { session ->
        val sessionYear = Calendar.getInstance().apply { timeInMillis = session.startTime }.get(Calendar.YEAR)
        if (sessionYear != currentYear && sessionYear != lastYear) {
            result.add(HistoryListItem.YearLabel(sessionYear))
            lastYear = sessionYear
        }
        result.add(HistoryListItem.SessionItem(session))
    }
    return result
}

@Composable
private fun HistoryScreen(
    sessions: List<Session>,
    activeSessionId: Long?,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onEditSession: (Session) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp)
                )
                Button(onClick = onSync, enabled = !isSyncing) {
                    Text(stringResource(if (isSyncing) R.string.sync_button_syncing else R.string.sync_button))
                }
            }
        }
        return
    }

    PullToRefreshHistoryList(sessions, activeSessionId, isSyncing, onSync, onEditSession)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshHistoryList(
    sessions: List<Session>,
    activeSessionId: Long?,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onEditSession: (Session) -> Unit
) {
    val historyItems = remember(sessions) { buildHistoryItems(sessions) }
    PullToRefreshBox(isRefreshing = isSyncing, onRefresh = onSync, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(historyItems, key = { item ->
                when (item) {
                    is HistoryListItem.SessionItem -> item.session.id
                    is HistoryListItem.YearLabel -> "year-${item.year}"
                }
            }) { item ->
                when (item) {
                    is HistoryListItem.YearLabel -> Text(
                        text = item.year.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is HistoryListItem.SessionItem -> SessionCard(
                        session = item.session,
                        isActive = item.session.id == activeSessionId,
                        onClick = { onEditSession(item.session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session, isActive: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("d MMM", locale) }
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val unitH = stringResource(R.string.time_h)
    val unitM = stringResource(R.string.time_m)
    val durationText = remember(session, unitH, unitM) { formatDuration(session.startTime, session.lastShotTime, unitH, unitM) }

    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = Color(0xFF4CAF50), shape = CircleShape)
                    )
                }
                Text(dateFormat.format(Date(session.startTime)), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${timeFormat.format(Date(session.startTime))} – ${timeFormat.format(Date(session.lastShotTime))}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(durationText, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.shots_count, session.shotCount), style = MaterialTheme.typography.bodyMedium)
            if (session.shotsPerEndAtStart > 0) {
                Text(
                    stringResource(R.string.shots_per_end_count, session.shotsPerEndAtStart),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatDuration(startTime: Long, endTime: Long, unitH: String, unitM: String): String {
    val durationMin = (endTime - startTime) / 60_000L
    return when {
        durationMin < 1 -> "<1$unitM"
        durationMin < 60 -> "${durationMin}$unitM"
        else -> {
            val h = durationMin / 60
            val m = durationMin % 60
            if (m == 0L) "${h}$unitH" else "${h}$unitH ${m}$unitM"
        }
    }
}

@Composable
private fun EditSessionDialog(
    session: Session,
    onShowDetail: () -> Unit,
    onSave: (Session) -> Unit,
    onDelete: (Session) -> Unit,
    onDismiss: () -> Unit
) {
    var count by remember { mutableIntStateOf(session.shotCount) }
    var startTime by remember { mutableLongStateOf(session.startTime) }
    var endTime by remember { mutableLongStateOf(session.lastShotTime) }
    var shotsPerEnd by remember { mutableIntStateOf(session.shotsPerEndAtStart) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(session.startTime, locale) { SimpleDateFormat("d MMM", locale) }
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dateFormat.format(Date(session.startTime))) },
        text = {
            Column {
                OutlinedButton(onClick = onShowDetail, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.details_button))
                }
                StepperRow(stringResource(R.string.edit_shots), count, onDec = { if (count > 0) count-- }, onInc = { count++ })
                DateTimeField(
                    label = stringResource(R.string.edit_start_time),
                    millis = startTime,
                    locale = locale,
                    is24Hour = is24Hour,
                    onChange = { startTime = it }
                )
                DateTimeField(
                    label = stringResource(R.string.edit_end_time),
                    millis = endTime,
                    locale = locale,
                    is24Hour = is24Hour,
                    onChange = { endTime = it }
                )
                StepperRow(
                    stringResource(R.string.edit_shots_per_end),
                    shotsPerEnd,
                    onDec = { if (shotsPerEnd > 0) shotsPerEnd-- },
                    onInc = { shotsPerEnd++ },
                    zeroLabel = stringResource(R.string.edit_off)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    session.copy(
                        shotCount = count,
                        startTime = startTime,
                        lastShotTime = endTime,
                        shotsPerEndAtStart = shotsPerEnd
                    )
                )
            }) { Text(stringResource(R.string.save_button)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(session) }) { Text(stringResource(R.string.delete_button)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
            }
        }
    )
}

/** DatePickerState represents the picked date as UTC-midnight millis regardless of the device's
 * timezone, so the calendar-date it reports has to be read back out via a UTC Calendar and
 * re-combined with the wall-clock time-of-day through a local one — mixing the two directly
 * would shift the date by the timezone offset. */
private fun utcMidnightOf(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    return utc.timeInMillis
}

private fun combineDateAndTime(utcMidnightMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMillis }
    val local = Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute)
    }
    return local.timeInMillis
}

private enum class PickerStep { DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    label: String,
    millis: Long,
    locale: Locale,
    is24Hour: Boolean,
    onChange: (Long) -> Unit
) {
    var step by remember { mutableStateOf<PickerStep?>(null) }
    var pendingDateMillis by remember { mutableLongStateOf(0L) }
    val format = remember(locale) { SimpleDateFormat("d MMM, HH:mm", locale) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { step = PickerStep.DATE }) {
            Text(format.format(Date(millis)))
        }
    }

    if (step == PickerStep.DATE) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOf(millis))
        DatePickerDialog(
            onDismissRequest = { step = null },
            confirmButton = {
                TextButton(onClick = {
                    pendingDateMillis = dateState.selectedDateMillis ?: utcMidnightOf(millis)
                    step = PickerStep.TIME
                }) { Text(stringResource(R.string.ok_button)) }
            },
            dismissButton = { TextButton(onClick = { step = null }) { Text(stringResource(R.string.cancel_button)) } }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (step == PickerStep.TIME) {
        val cal = remember { Calendar.getInstance().apply { timeInMillis = millis } }
        val initialHour24 = cal.get(Calendar.HOUR_OF_DAY)
        val (initialHour12, initialIsPm) = hour24To12(initialHour24)

        var hourIndex by remember { mutableIntStateOf(if (is24Hour) initialHour24 else initialHour12 - 1) }
        var minuteIndex by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }
        var periodIndex by remember { mutableIntStateOf(if (initialIsPm) 1 else 0) }

        Dialog(onDismissRequest = { step = null }) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WheelColumn(
                            values = if (is24Hour) (0..23).map { it.toString().padStart(2, '0') } else (1..12).map { it.toString() },
                            selectedIndex = hourIndex,
                            onSelectedIndexChange = { hourIndex = it },
                            modifier = Modifier.width(56.dp)
                        )
                        Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
                        WheelColumn(
                            values = (0..59).map { it.toString().padStart(2, '0') },
                            selectedIndex = minuteIndex,
                            onSelectedIndexChange = { minuteIndex = it },
                            modifier = Modifier.width(56.dp)
                        )
                        if (!is24Hour) {
                            Spacer(modifier = Modifier.width(12.dp))
                            WheelColumn(
                                values = listOf("AM", "PM"),
                                selectedIndex = periodIndex,
                                onSelectedIndexChange = { periodIndex = it },
                                modifier = Modifier.width(56.dp)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { step = null }) { Text(stringResource(R.string.cancel_button)) }
                        TextButton(onClick = {
                            val hour24 = if (is24Hour) hourIndex else hour12ToHour24(hourIndex + 1, periodIndex == 1)
                            onChange(combineDateAndTime(pendingDateMillis, hour24, minuteIndex))
                            step = null
                        }) { Text(stringResource(R.string.ok_button)) }
                    }
                }
            }
        }
    }
}

/** Returns (hour 1-12, isPm) for a 24-hour hour value. */
private fun hour24To12(hour24: Int): Pair<Int, Boolean> {
    val isPm = hour24 >= 12
    val hour12 = hour24 % 12
    return (if (hour12 == 0) 12 else hour12) to isPm
}

private fun hour12ToHour24(hour12: Int, isPm: Boolean): Int {
    val hour = hour12 % 12
    return if (isPm) hour + 12 else hour
}

private const val WHEEL_ITEM_HEIGHT_DP = 40
private const val WHEEL_VISIBLE_COUNT = 5

/** A scrolling drum/wheel picker: snaps to whichever item sits nearest the centered highlight
 * band. Built by hand rather than pulling in a picker library or Material3's clock-dial
 * TimePicker, which is awkward to operate precisely — matches how charts elsewhere in this app
 * are hand-rolled on Canvas instead of via a dependency. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = WHEEL_ITEM_HEIGHT_DP.dp
    val visibleHeight = itemHeight * WHEEL_VISIBLE_COUNT
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)

    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf selectedIndex
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - center) }?.index ?: selectedIndex
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) onSelectedIndexChange(centerIndex)
    }

    Box(modifier = modifier.height(visibleHeight), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color(0xFF333333), RoundedCornerShape(8.dp))
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = (visibleHeight - itemHeight) / 2),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(values) { index, label ->
                val selected = index == centerIndex
                Box(
                    modifier = Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = if (selected) 22.sp else 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF8A8A8A)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClearDataConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.clear_data_title)) },
        text = { Text(stringResource(R.string.clear_data_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.clear_data_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_button)) } }
    )
}

@Composable
private fun FormatChooserRow(format: DataFormat, onFormatChange: (DataFormat) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = format == DataFormat.JSON, onClick = { onFormatChange(DataFormat.JSON) }, label = { Text(stringResource(R.string.format_json)) })
        FilterChip(selected = format == DataFormat.CSV, onClick = { onFormatChange(DataFormat.CSV) }, label = { Text(stringResource(R.string.format_csv)) })
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (DataFormat) -> Unit) {
    var format by remember { mutableStateOf(DataFormat.JSON) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_button)) },
        text = { FormatChooserRow(format) { format = it } },
        confirmButton = { TextButton(onClick = { onExport(format) }) { Text(stringResource(R.string.export_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } }
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (DataFormat) -> Unit) {
    var format by remember { mutableStateOf(DataFormat.JSON) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_button)) },
        text = { FormatChooserRow(format) { format = it } },
        confirmButton = { TextButton(onClick = { onImport(format) }) { Text(stringResource(R.string.import_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } }
    )
}

@Composable
private fun LanguagePickerDialog(
    currentLanguage: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lang_menu)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { lang ->
                    val mainName = if (lang == AppLanguage.SYSTEM) stringResource(R.string.lang_system) else lang.nativeName
                    val subtitle = if (lang != AppLanguage.SYSTEM && lang.nativeName != lang.englishName) lang.englishName else null
                    TextButton(
                        onClick = { onSelect(lang) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (lang == currentLanguage) "• $mainName" else mainName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } }
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

