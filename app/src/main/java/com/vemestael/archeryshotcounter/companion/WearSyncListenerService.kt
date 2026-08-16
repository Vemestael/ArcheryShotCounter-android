package com.vemestael.archeryshotcounter.companion

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.Executors

/** Receives one DataItem per session, path "/session/<id>", a "json" field shaped like SessionSync. */
class WearSyncListenerService : WearableListenerService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val jsonPayloads = dataEvents.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            if (!event.dataItem.uri.path.orEmpty().startsWith("/session")) return@mapNotNull null
            DataMapItem.fromDataItem(event.dataItem).dataMap.getString("json")
        }
        dataEvents.release()
        if (jsonPayloads.isEmpty()) return

        val db = AppDatabase.getInstance(applicationContext)
        executor.execute {
            jsonPayloads.forEach { json ->
                val (session, shots) = parseSessionJson(json)
                db.replaceSession(session, shots)
            }
        }
    }
}
