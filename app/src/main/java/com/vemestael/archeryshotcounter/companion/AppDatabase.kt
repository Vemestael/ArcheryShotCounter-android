package com.vemestael.archeryshotcounter.companion

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY startTime DESC")
    fun getAll(): List<Session>

    @Query("SELECT * FROM sessions")
    fun getAllIncludingDeleted(): List<Session>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getById(id: Long): Session?

    @Upsert
    fun insertOrUpdate(session: Session)

    @Query("DELETE FROM sessions")
    fun deleteAll()
}

@Dao
interface ShotDao {
    @Insert
    fun insertAll(shots: List<Shot>)

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY timestamp")
    fun getBySession(sessionId: Long): List<Shot>

    @Query("SELECT * FROM shots ORDER BY sessionId, timestamp")
    fun getAll(): List<Shot>

    @Query("DELETE FROM shots WHERE sessionId = :sessionId")
    fun deleteAllForSession(sessionId: Long)

    @Query("DELETE FROM shots")
    fun deleteAll()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `sessions` ADD COLUMN `lastModified` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `sessions` ADD COLUMN `deletedAt` INTEGER")
    }
}

@Database(entities = [Session::class, Shot::class], version = 2, exportSchema = false)
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
                ).addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }

    /** Applies an incoming session from the watch only if it's newer (last-write-wins,
     * tombstones included) than whatever's stored locally under the same id. */
    fun mergeIncomingSession(incoming: Session, incomingShots: List<Shot>) {
        runInTransaction {
            val local = sessionDao().getById(incoming.id)
            if (local != null && local.lastModified >= incoming.lastModified) return@runInTransaction
            sessionDao().insertOrUpdate(incoming)
            shotDao().deleteAllForSession(incoming.id)
            if (incoming.deletedAt == null && incomingShots.isNotEmpty()) {
                shotDao().insertAll(incomingShots)
            }
        }
    }

    /** Pure local wipe — does NOT write tombstones, so it must never be synced as a set of
     * deletions to the other device (that would wipe the other device's data too). */
    fun clearAllLocalData() {
        runInTransaction {
            shotDao().deleteAll()
            sessionDao().deleteAll()
        }
    }
}
