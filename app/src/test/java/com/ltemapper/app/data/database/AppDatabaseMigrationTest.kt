package com.ltemapper.app.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards MIGRATION_1_2 against silently losing existing user data (sessions, measurements) when
 * the schema changes — the failure mode this catches is `fallbackToDestructiveMigration()`-style
 * data loss on a real user's device, which nothing else in the test suite would notice since
 * every other Room test starts from a fresh in-memory database at the *current* schema version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun `migrating 1 to 2 preserves existing sessions and measurements`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO mapping_sessions (id, startTime, endTime, distanceMeters, measurementCount) " +
                    "VALUES (1, 1000, 2000, 50.0, 1)",
            )
            execSQL(
                "INSERT INTO signal_measurements " +
                    "(id, sessionId, timestamp, latitude, longitude, isLowAccuracy, networkType, signalQuality) " +
                    "VALUES (1, 1, 1500, 43.1, 2.3, 0, 'LTE', 'GOOD')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        val sessions = migrated.query("SELECT id, startTime, endTime, distanceMeters FROM mapping_sessions")
        assertTrue("existing session row must survive the migration", sessions.moveToFirst())
        assertEquals(1L, sessions.getLong(0))
        assertEquals(1000L, sessions.getLong(1))
        assertEquals(2000L, sessions.getLong(2))
        assertEquals(50.0, sessions.getDouble(3), 0.001)
        sessions.close()

        val measurements = migrated.query("SELECT id, sessionId, latitude, longitude FROM signal_measurements")
        assertTrue("existing measurement row must survive the migration", measurements.moveToFirst())
        assertEquals(1L, measurements.getLong(0))
        assertEquals(1L, measurements.getLong(1))
        measurements.close()
    }

    @Test
    fun `migrating 1 to 2 adds the offline_regions table`() {
        helper.createDatabase(TEST_DB, 1).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        val tables = migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'offline_regions'")
        assertTrue("MIGRATION_1_2 must create offline_regions", tables.moveToFirst())
        tables.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
