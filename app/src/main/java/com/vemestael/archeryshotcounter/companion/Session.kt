package com.vemestael.archeryshotcounter.companion

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: Long,
    val startTime: Long,
    val lastShotTime: Long,
    val shotCount: Int,
    val shotsPerEndAtStart: Int = 0
)
