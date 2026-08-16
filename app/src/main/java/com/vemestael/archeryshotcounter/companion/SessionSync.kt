package com.vemestael.archeryshotcounter.companion

import org.json.JSONArray
import org.json.JSONObject

private fun sessionJson(session: Session, shots: List<Shot>): JSONObject {
    val shotsArray = JSONArray()
    shots.sortedBy { it.timestamp }.forEach { shot ->
        shotsArray.put(
            JSONObject().apply {
                put("timestamp", shot.timestamp)
                put("magnitude", shot.magnitude?.toDouble() ?: JSONObject.NULL)
            }
        )
    }
    return JSONObject().apply {
        put("id", session.id)
        put("startTime", session.startTime)
        put("lastShotTime", session.lastShotTime)
        put("shotCount", session.shotCount)
        put("shotsPerEndAtStart", session.shotsPerEndAtStart)
        put("lastModified", session.lastModified)
        put("deletedAt", session.deletedAt ?: JSONObject.NULL)
        put("shots", shotsArray)
    }
}

private fun sessionFromJson(obj: JSONObject): Pair<Session, List<Shot>> {
    val session = Session(
        id = obj.getLong("id"),
        startTime = obj.getLong("startTime"),
        lastShotTime = obj.getLong("lastShotTime"),
        shotCount = obj.getInt("shotCount"),
        shotsPerEndAtStart = obj.optInt("shotsPerEndAtStart", 0),
        lastModified = obj.optLong("lastModified", 0L),
        deletedAt = if (obj.isNull("deletedAt")) null else obj.optLong("deletedAt")
    )
    val shotsArray = obj.getJSONArray("shots")
    val shots = List(shotsArray.length()) { i ->
        val shotObj = shotsArray.getJSONObject(i)
        Shot(
            sessionId = session.id,
            timestamp = shotObj.getLong("timestamp"),
            magnitude = if (shotObj.isNull("magnitude")) null else shotObj.getDouble("magnitude").toFloat()
        )
    }
    return session to shots
}

/** Single-session payload shape carried by each Data Layer DataItem, in both sync directions. */
fun buildSessionJson(session: Session, shots: List<Shot>): String = sessionJson(session, shots).toString()

fun parseSessionJson(json: String): Pair<Session, List<Shot>> = sessionFromJson(JSONObject(json))

data class ImportedSession(val session: Session, val shots: List<Shot>)

/** Full-backup file shape. Tombstoned sessions are excluded — a deleted session isn't a backup you want back. */
fun buildExportJson(sessions: List<Session>, shotsBySession: Map<Long, List<Shot>>): String {
    val sessionsArray = JSONArray()
    sessions.filter { it.deletedAt == null }
        .sortedByDescending { it.startTime }
        .forEach { session -> sessionsArray.put(sessionJson(session, shotsBySession[session.id].orEmpty())) }
    return JSONObject().apply {
        put("exportedAt", System.currentTimeMillis())
        put("sessions", sessionsArray)
    }.toString(2)
}

fun parseImportJson(json: String): List<ImportedSession> {
    val sessionsArray = JSONObject(json).getJSONArray("sessions")
    return List(sessionsArray.length()) { i ->
        val (session, shots) = sessionFromJson(sessionsArray.getJSONObject(i))
        ImportedSession(session, shots)
    }
}
