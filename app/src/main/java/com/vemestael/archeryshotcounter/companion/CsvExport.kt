package com.vemestael.archeryshotcounter.companion

private const val CSV_HEADER = "session_id,session_start,shots_per_end,shot_timestamp,shot_magnitude"

/** One row per shot; tombstoned sessions are excluded, same as JSON export. A session with no
 * shots still gets one row (blank shot columns) so it isn't silently dropped. */
fun buildExportCsv(sessions: List<Session>, shotsBySession: Map<Long, List<Shot>>): String {
    val lines = StringBuilder(CSV_HEADER).append('\n')
    sessions.filter { it.deletedAt == null }
        .sortedByDescending { it.startTime }
        .forEach { session ->
            val shots = shotsBySession[session.id].orEmpty().sortedBy { it.timestamp }
            if (shots.isEmpty()) {
                lines.append(session.id).append(',')
                    .append(session.startTime).append(',')
                    .append(session.shotsPerEndAtStart).append(",,\n")
            } else {
                shots.forEach { shot ->
                    lines.append(session.id).append(',')
                        .append(session.startTime).append(',')
                        .append(session.shotsPerEndAtStart).append(',')
                        .append(shot.timestamp).append(',')
                        .append(shot.magnitude?.toString().orEmpty())
                        .append('\n')
                }
            }
        }
    return lines.toString()
}

fun parseImportCsv(csv: String): List<ImportedSession> {
    val lines = csv.lineSequence().filter { it.isNotBlank() }.drop(1) // skip header
    data class Row(val sessionId: Long, val startTime: Long, val shotsPerEnd: Int, val timestamp: Long?, val magnitude: Float?)

    val rows = lines.map { line ->
        val cols = line.split(',')
        Row(
            sessionId = cols[0].trim().toLong(),
            startTime = cols[1].trim().toLong(),
            shotsPerEnd = cols[2].trim().toInt(),
            timestamp = cols.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }?.toLong(),
            magnitude = cols.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }?.toFloat()
        )
    }.toList()

    return rows.groupBy { it.sessionId }.map { (sessionId, sessionRows) ->
        val first = sessionRows.first()
        val shots = sessionRows.mapNotNull { row ->
            row.timestamp?.let { ts -> Shot(sessionId = sessionId, timestamp = ts, magnitude = row.magnitude) }
        }
        val lastShotTime = shots.maxOfOrNull { it.timestamp } ?: first.startTime
        val now = System.currentTimeMillis()
        ImportedSession(
            session = Session(
                id = sessionId,
                startTime = first.startTime,
                lastShotTime = lastShotTime,
                shotCount = shots.size,
                shotsPerEndAtStart = first.shotsPerEnd,
                lastModified = now
            ),
            shots = shots
        )
    }
}
