package com.vemestael.archeryshotcounter.companion

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val TAG = "ArcheryCompanionSync"

/** Persists any session DataItems found in the buffer. Runs the actual DB write on [executor]. */
fun persistSessionDataEvents(context: Context, dataEvents: DataEventBuffer, executor: Executor) {
    val jsonPayloads = dataEvents.mapNotNull { event ->
        if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
        if (!event.dataItem.uri.path.orEmpty().startsWith("/session")) return@mapNotNull null
        DataMapItem.fromDataItem(event.dataItem).dataMap.getString("json")
    }
    if (jsonPayloads.isEmpty()) return

    val db = AppDatabase.getInstance(context.applicationContext)
    executor.execute {
        jsonPayloads.forEach { json ->
            try {
                val (session, shots) = parseSessionJson(json)
                db.mergeIncomingSession(session, shots)
            } catch (e: Exception) {
                Log.e(TAG, "failed to persist session payload", e)
            }
        }
    }
}

/** Background delivery path: Play Services binds this even when the app isn't running. */
class WearSyncListenerService : WearableListenerService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        persistSessionDataEvents(this, dataEvents, executor)
        dataEvents.release()
    }
}
