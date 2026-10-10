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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.vemestael.archeryshotcounter.R
import com.vemestael.archeryshotcounter.companion.theme.AppButton
import com.vemestael.archeryshotcounter.companion.theme.AppCard
import com.vemestael.archeryshotcounter.companion.theme.AppDialog
import com.vemestael.archeryshotcounter.companion.theme.AppList
import com.vemestael.archeryshotcounter.companion.theme.AppScreen
import com.vemestael.archeryshotcounter.companion.theme.ArcheryShotCounterTheme
import com.vemestael.archeryshotcounter.companion.theme.DialogActions
import com.vemestael.archeryshotcounter.companion.theme.DialogText
import com.vemestael.archeryshotcounter.companion.theme.EditButton
import com.vemestael.archeryshotcounter.companion.theme.IbmPlexMono
import com.vemestael.archeryshotcounter.companion.theme.IconTapTarget
import com.vemestael.archeryshotcounter.companion.theme.LocalAppPalette
import com.vemestael.archeryshotcounter.companion.theme.MoreIcon
import com.vemestael.archeryshotcounter.companion.theme.PaletteChoice
import com.vemestael.archeryshotcounter.companion.theme.SectionTitle
import com.vemestael.archeryshotcounter.companion.theme.StatusIndicator
import com.vemestael.archeryshotcounter.companion.theme.Stepper
import com.vemestael.archeryshotcounter.companion.theme.WheelPicker
import com.vemestael.archeryshotcounter.companion.theme.inlineTapTarget
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
private const val KEY_PALETTE = "palette"

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

    /** Unlike [changeLanguage], no recreate() needed — Compose just recomposes with the new palette. */
    private fun changePalette(choice: PaletteChoice) {
        paletteChoice = choice
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putString(KEY_PALETTE, choice.name) }
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
    private var showThemePicker by mutableStateOf(false)
    private var paletteChoice by mutableStateOf(PaletteChoice.BRASS)

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
        paletteChoice = PaletteChoice.entries.find {
            it.name == getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PALETTE, null)
        } ?: PaletteChoice.BRASS
        setContent {
            ArcheryShotCounterTheme(palette = paletteChoice.palette) {
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
                    HomeScreen(
                        sessions = sessions,
                        activeSessionId = activeSessionId,
                        isSyncing = isSyncing,
                        dataStatus = dataStatus,
                        onSync = ::syncData,
                        onOpenExportDialog = { showExportDialog = true },
                        onOpenImportDialog = { showImportDialog = true },
                        onOpenLanguagePicker = { showLanguagePicker = true },
                        onOpenThemePicker = { showThemePicker = true },
                        onShowDetail = { detailSession = it },
                        onEditSession = { editingSession = it },
                        onClearData = { showClearDataConfirm = true }
                    )
                    editingSession?.let { session ->
                        EditSessionDialog(
                            session = session,
                            onSave = { saveSessionEdit(session, it); editingSession = null },
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
                    if (showThemePicker) {
                        ThemePickerDialog(
                            currentPalette = paletteChoice,
                            onSelect = { choice ->
                                showThemePicker = false
                                changePalette(choice)
                            },
                            onDismiss = { showThemePicker = false }
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

    /** [original] is the session as it was before the edit dialog opened it, used only to detect
     * what changed (shot count, narrowed start/end) so the underlying shot rows can be kept
     * consistent with it — never persisted itself. Same reconciliation as the watch's editSession. */
    private fun saveSessionEdit(original: Session, edited: Session) {
        val updated = edited.copy(lastModified = System.currentTimeMillis())
        val countDelta = updated.shotCount - original.shotCount
        val db = AppDatabase.getInstance(applicationContext)
        dbExecutor.execute {
            db.runInTransaction {
                db.sessionDao().insertOrUpdate(updated)
                // Shots added/removed by hand through the counter (not real detections) are
                // timestamped at the session's end so they show up in the per-shot history too.
                if (countDelta > 0) {
                    repeat(countDelta) {
                        db.shotDao().insert(Shot(sessionId = updated.id, timestamp = updated.lastShotTime, magnitude = null))
                    }
                } else if (countDelta < 0) {
                    db.shotDao().deleteLatest(updated.id, -countDelta)
                }
                // Narrowing the window (end moved earlier / start moved later) can leave shots
                // outside it — pull those back inside instead of leaving them stranded.
                if (updated.lastShotTime < original.lastShotTime) {
                    db.shotDao().clampTimestampsAfter(updated.id, updated.lastShotTime)
                }
                if (updated.startTime > original.startTime) {
                    db.shotDao().clampTimestampsBefore(updated.id, updated.startTime)
                }
            }
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

@Composable
private fun HomeScreen(
    sessions: List<Session>,
    activeSessionId: Long?,
    isSyncing: Boolean,
    dataStatus: String?,
    onSync: () -> Unit,
    onOpenExportDialog: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onShowDetail: (Session) -> Unit,
    onEditSession: (Session) -> Unit,
    onClearData: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val palette = LocalAppPalette.current

    AppScreen(
        title = stringResource(R.string.app_name),
        actions = {
            Box {
                IconTapTarget(onClick = { showMenu = true }) {
                    MoreIcon(color = palette.textDim, modifier = Modifier.size(24.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = palette.bgElev2
                ) {
                    MenuItem(stringResource(R.string.export_button)) { showMenu = false; onOpenExportDialog() }
                    MenuItem(stringResource(R.string.import_button)) { showMenu = false; onOpenImportDialog() }
                    MenuItem(stringResource(R.string.lang_menu)) { showMenu = false; onOpenLanguagePicker() }
                    MenuItem(stringResource(R.string.appearance_title)) { showMenu = false; onOpenThemePicker() }
                    MenuItem(stringResource(R.string.clear_data_menu), color = palette.pause) { showMenu = false; onClearData() }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (dataStatus != null) {
                Text(
                    text = dataStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                HistoryScreen(sessions, activeSessionId, isSyncing, onSync, onShowDetail, onEditSession)
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, color: Color = LocalAppPalette.current.text, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = color) },
        onClick = onClick
    )
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
    onShowDetail: (Session) -> Unit,
    onEditSession: (Session) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalAppPalette.current.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                AppButton(
                    text = stringResource(if (isSyncing) R.string.sync_button_syncing else R.string.sync_button),
                    onClick = onSync,
                    enabled = !isSyncing,
                    selected = true,
                    modifier = Modifier
                )
            }
        }
        return
    }

    val historyItems = remember(sessions) { buildHistoryItems(sessions) }
    AppList(isRefreshing = isSyncing, onRefresh = onSync) {
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
                    color = LocalAppPalette.current.textMuted
                )
                is HistoryListItem.SessionItem -> SessionCard(
                    session = item.session,
                    isActive = item.session.id == activeSessionId,
                    onClick = { onShowDetail(item.session) },
                    onEdit = { onEditSession(item.session) }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session, isActive: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val palette = LocalAppPalette.current
    val dateFormat = remember(locale) { SimpleDateFormat("d MMM", locale) }
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val unitH = stringResource(R.string.time_h)
    val unitM = stringResource(R.string.time_m)
    val durationText = remember(session, unitH, unitM) { formatDuration(session.startTime, session.lastShotTime, unitH, unitM) }

    val seriesSize = session.shotsPerEndAtStart
    val durationAndSeriesText = if (seriesSize > 0)
        "$durationText • ${stringResource(R.string.history_series_size, seriesSize)}"
    else
        durationText

    // Same layout as the watch's history card: date/time/duration stacked on the left, the shot
    // count as a big mono number on the right.
    AppCard(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // The pencil opens the editor; tapping anywhere else on the card opens the
                // session's shot details — same split as the watch's history card.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(
                        text = dateFormat.format(Date(session.startTime)),
                        dotColor = if (isActive) palette.active else palette.textMuted,
                        textColor = palette.text,
                        textStyle = MaterialTheme.typography.titleMedium
                    )
                    EditButton(
                        onClick = onEdit,
                        color = palette.textDim,
                        modifier = Modifier.padding(start = 8.dp).inlineTapTarget(24.dp)
                    )
                }
                Text(
                    "${timeFormat.format(Date(session.startTime))} – ${timeFormat.format(Date(session.lastShotTime))}",
                    fontFamily = IbmPlexMono,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text
                )
                Text(durationAndSeriesText, style = MaterialTheme.typography.bodyMedium, color = palette.textDim)
            }
            Text(
                text = "${session.shotCount}",
                fontFamily = IbmPlexMono,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier.padding(start = 12.dp)
            )
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

    AppDialog(onDismissRequest = onDismiss) {
        SectionTitle(text = dateFormat.format(Date(session.startTime)), modifier = Modifier, textAlign = TextAlign.Center)
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
        FieldLabel(stringResource(R.string.edit_shots))
        Stepper(value = "$count", onDecrement = { if (count > 0) count-- }, onIncrement = { count++ })
        FieldLabel(stringResource(R.string.edit_shots_per_end))
        Stepper(
            value = if (shotsPerEnd == 0) stringResource(R.string.edit_off) else "$shotsPerEnd",
            onDecrement = { if (shotsPerEnd > 0) shotsPerEnd-- },
            onIncrement = { shotsPerEnd++ },
            valueFontSize = if (shotsPerEnd == 0) 16.sp else 22.sp
        )
        // No Cancel button, same as the watch's editor: back gesture or a tap outside the
        // dialog dismisses it (AppDialog's Dialog defaults) without saving.
        AppButton(
            text = stringResource(R.string.save_button),
            onClick = {
                // Same rule as the watch: an end at or before the start is treated as spilling
                // into the next day rather than producing a negative duration.
                val normalizedEndTime =
                    if (endTime <= startTime) endTime + 24L * 60 * 60 * 1000 else endTime
                onSave(
                    session.copy(
                        shotCount = count,
                        startTime = startTime,
                        lastShotTime = normalizedEndTime,
                        shotsPerEndAtStart = shotsPerEnd
                    )
                )
            },
            selected = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        AppButton(
            text = stringResource(R.string.delete_button),
            onClick = { onDelete(session) },
            destructive = true
        )
    }
}

/** A dim label above a dialog field (stepper, date-time button). */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalAppPalette.current.textDim,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
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
    val palette = LocalAppPalette.current

    FieldLabel(label)
    AppButton(onClick = { step = PickerStep.DATE }) {
        Text(format.format(Date(millis)), fontFamily = IbmPlexMono)
    }

    if (step == PickerStep.DATE) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOf(millis))
        DatePickerDialog(
            onDismissRequest = { step = null },
            colors = DatePickerDefaults.colors(containerColor = palette.bgElev),
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.ok_button),
                    onClick = {
                        pendingDateMillis = dateState.selectedDateMillis ?: utcMidnightOf(millis)
                        step = PickerStep.TIME
                    },
                    selected = true,
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.cancel_button),
                    onClick = { step = null },
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                )
            }
        ) {
            DatePicker(
                state = dateState,
                colors = DatePickerDefaults.colors(containerColor = palette.bgElev)
            )
        }
    }

    if (step == PickerStep.TIME) {
        val cal = remember { Calendar.getInstance().apply { timeInMillis = millis } }
        val initialHour24 = cal.get(Calendar.HOUR_OF_DAY)
        val (initialHour12, initialIsPm) = hour24To12(initialHour24)

        var hourIndex by remember { mutableIntStateOf(if (is24Hour) initialHour24 else initialHour12 - 1) }
        var minuteIndex by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }
        var periodIndex by remember { mutableIntStateOf(if (initialIsPm) 1 else 0) }

        AppDialog(onDismissRequest = { step = null }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WheelPicker(
                    values = if (is24Hour) (0..23).map { it.toString().padStart(2, '0') } else (1..12).map { it.toString() },
                    selectedIndex = hourIndex,
                    onSelectedIndexChange = { hourIndex = it },
                    modifier = Modifier.width(56.dp)
                )
                Text(
                    ":",
                    fontFamily = IbmPlexMono,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                WheelPicker(
                    values = (0..59).map { it.toString().padStart(2, '0') },
                    selectedIndex = minuteIndex,
                    onSelectedIndexChange = { minuteIndex = it },
                    modifier = Modifier.width(56.dp)
                )
                if (!is24Hour) {
                    Spacer(modifier = Modifier.width(12.dp))
                    WheelPicker(
                        values = listOf("AM", "PM"),
                        selectedIndex = periodIndex,
                        onSelectedIndexChange = { periodIndex = it },
                        modifier = Modifier.width(56.dp)
                    )
                }
            }
            DialogActions(
                dismissText = stringResource(R.string.cancel_button),
                onDismiss = { step = null },
                confirmText = stringResource(R.string.ok_button),
                onConfirm = {
                    val hour24 = if (is24Hour) hourIndex else hour12ToHour24(hourIndex + 1, periodIndex == 1)
                    onChange(combineDateAndTime(pendingDateMillis, hour24, minuteIndex))
                    step = null
                }
            )
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

@Composable
private fun ClearDataConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AppDialog(onDismissRequest = onCancel) {
        SectionTitle(text = stringResource(R.string.clear_data_title), modifier = Modifier, textAlign = TextAlign.Center)
        DialogText(stringResource(R.string.clear_data_message))
        DialogActions(
            dismissText = stringResource(R.string.cancel_button),
            onDismiss = onCancel,
            confirmText = stringResource(R.string.clear_data_confirm),
            onConfirm = onConfirm,
            destructive = true
        )
    }
}

@Composable
private fun FormatChooserRow(format: DataFormat, onFormatChange: (DataFormat) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppButton(
            text = stringResource(R.string.format_json),
            onClick = { onFormatChange(DataFormat.JSON) },
            selected = format == DataFormat.JSON,
            modifier = Modifier.weight(1f)
        )
        AppButton(
            text = stringResource(R.string.format_csv),
            onClick = { onFormatChange(DataFormat.CSV) },
            selected = format == DataFormat.CSV,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Export and Import share one dialog shape: a title, the JSON/CSV choice and cancel/confirm. */
@Composable
private fun DataFormatDialog(title: String, onDismiss: () -> Unit, onConfirm: (DataFormat) -> Unit) {
    var format by remember { mutableStateOf(DataFormat.JSON) }
    AppDialog(onDismissRequest = onDismiss) {
        SectionTitle(text = title, modifier = Modifier, textAlign = TextAlign.Center)
        FormatChooserRow(format) { format = it }
        DialogActions(
            dismissText = stringResource(R.string.cancel_button),
            onDismiss = onDismiss,
            confirmText = title,
            onConfirm = { onConfirm(format) }
        )
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (DataFormat) -> Unit) {
    DataFormatDialog(title = stringResource(R.string.export_button), onDismiss = onDismiss, onConfirm = onExport)
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (DataFormat) -> Unit) {
    DataFormatDialog(title = stringResource(R.string.import_button), onDismiss = onDismiss, onConfirm = onImport)
}

@Composable
private fun LanguagePickerDialog(
    currentLanguage: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(onDismissRequest = onDismiss, verticalSpacing = 8.dp) {
        SectionTitle(text = stringResource(R.string.lang_menu), modifier = Modifier, textAlign = TextAlign.Center)
        AppLanguage.entries.forEach { lang ->
            val mainName = if (lang == AppLanguage.SYSTEM) stringResource(R.string.lang_system) else lang.nativeName
            val subtitle = if (lang != AppLanguage.SYSTEM && lang.nativeName != lang.englishName) lang.englishName else null
            AppButton(onClick = { onSelect(lang) }, selected = lang == currentLanguage) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mainName, style = MaterialTheme.typography.bodyLarge)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        DialogActions(dismissText = stringResource(R.string.cancel_button), onDismiss = onDismiss)
    }
}

/** The watch's theme picker as a dialog: each palette's name and five swatches of its colors. */
@Composable
private fun ThemePickerDialog(
    currentPalette: PaletteChoice,
    onSelect: (PaletteChoice) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(onDismissRequest = onDismiss, verticalSpacing = 8.dp) {
        SectionTitle(text = stringResource(R.string.appearance_title), modifier = Modifier, textAlign = TextAlign.Center)
        PaletteChoice.entries.forEach { choice ->
            AppButton(onClick = { onSelect(choice) }, selected = currentPalette == choice) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(choice.labelRes))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            choice.palette.bg,
                            choice.palette.bgElev,
                            choice.palette.accent,
                            choice.palette.pause,
                            choice.palette.active
                        ).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .border(1.dp, choice.palette.line, RoundedCornerShape(3.dp))
                                    .background(color = color, shape = RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
