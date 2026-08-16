package com.vemestael.archeryshotcounter.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSyncTest {

    @Test
    fun `parses a session payload matching the watch export shape`() {
        val json = """
            {
              "id": 100,
              "startTime": 100,
              "lastShotTime": 200,
              "shotCount": 2,
              "shotsPerEndAtStart": 3,
              "shots": [
                {"timestamp": 110, "magnitude": 24.97},
                {"timestamp": 150, "magnitude": null}
              ]
            }
        """.trimIndent()

        val (session, shots) = parseSessionJson(json)

        assertEquals(100L, session.id)
        assertEquals(3, session.shotsPerEndAtStart)
        assertEquals(2, shots.size)
        assertEquals(24.97f, shots[0].magnitude!!, 0.001f)
        assertNull(shots[1].magnitude)
    }

    @Test
    fun `shotsPerEndAtStart defaults to 0 when absent (legacy watch export)`() {
        val json = """{"id":1,"startTime":1,"lastShotTime":1,"shotCount":0,"shots":[]}"""
        val (session, _) = parseSessionJson(json)
        assertEquals(0, session.shotsPerEndAtStart)
    }
}
