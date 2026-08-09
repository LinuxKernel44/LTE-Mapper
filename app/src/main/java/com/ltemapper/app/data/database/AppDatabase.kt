package com.ltemapper.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ltemapper.app.data.dao.MappingSessionDao
import com.ltemapper.app.data.dao.OfflineRegionDao
import com.ltemapper.app.data.dao.SignalMeasurementDao
import com.ltemapper.app.data.entities.MappingSessionEntity
import com.ltemapper.app.data.entities.OfflineRegionEntity
import com.ltemapper.app.data.entities.SignalMeasurementEntity

@Database(
    entities = [MappingSessionEntity::class, SignalMeasurementEntity::class, OfflineRegionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mappingSessionDao(): MappingSessionDao
    abstract fun signalMeasurementDao(): SignalMeasurementDao
    abstract fun offlineRegionDao(): OfflineRegionDao

    companion object {
        const val DATABASE_NAME = "lte_mapper.db"

        /** Adds offline-map region tracking (Settings → Offline maps). No existing data touched. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_regions` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `minLat` REAL NOT NULL,
                        `maxLat` REAL NOT NULL,
                        `minLon` REAL NOT NULL,
                        `maxLon` REAL NOT NULL,
                        `minZoom` INTEGER NOT NULL,
                        `maxZoom` INTEGER NOT NULL,
                        `tileCount` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `downloadedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
