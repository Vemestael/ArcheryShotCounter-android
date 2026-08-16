package com.vemestael.archeryshotcounter.companion

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAll(): List<Session>

    @Upsert
    fun insertOrUpdate(session: Session)
}

@Dao
interface ShotDao {
    @Insert
    fun insertAll(shots: List<Shot>)

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY timestamp")
    fun getBySession(sessionId: Long): List<Shot>

    @Query("DELETE FROM shots WHERE sessionId = :sessionId")
    fun deleteAllForSession(sessionId: Long)
}

@Database(entities = [Session::class, Shot::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "archery-companion.db"
                ).build().also { instance = it }
            }
    }

    fun replaceSession(session: Session, shots: List<Shot>) {
        runInTransaction {
            sessionDao().insertOrUpdate(session)
            shotDao().deleteAllForSession(session.id)
            if (shots.isNotEmpty()) shotDao().insertAll(shots)
        }
    }
}
