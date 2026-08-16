package com.vemestael.archeryshotcounter.companion

import org.json.JSONObject

/** Mirrors the single-session shape the watch writes into each DataItem's "json" field. */
fun parseSessionJson(json: String): Pair<Session, List<Shot>> {
    val obj = JSONObject(json)
    val session = Session(
        id = obj.getLong("id"),
        startTime = obj.getLong("startTime"),
        lastShotTime = obj.getLong("lastShotTime"),
        shotCount = obj.getInt("shotCount"),
        shotsPerEndAtStart = obj.optInt("shotsPerEndAtStart", 0)
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
