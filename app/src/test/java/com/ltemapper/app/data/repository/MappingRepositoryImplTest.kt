package com.ltemapper.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ltemapper.app.data.database.AppDatabase
import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [MappingRepositoryImpl] against a real (in-memory) Room database via Robolectric,
 * covering database operations, session aggregation, and network-type filtering end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class MappingRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: MappingRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MappingRepositoryImpl(db.mappingSessionDao(), db.signalMeasurementDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun measurement(sessionId: Long, timestamp: Long, rsrp: Int?, quality: SignalQuality, type: RanType = RanType.LTE) = Measurement(
        sessionId = sessionId,
        timestamp = timestamp,
        latitude = 43.0 + timestamp * 0.0001,
        longitude = 2.0 + timestamp * 0.0001,
        accuracyMeters = 5f,
        isLowAccuracy = false,
        networkType = type,
        rssiDbm = -70,
        rsrpDbm = rsrp,
        rsrqDb = -10,
        rssnrDb = 15,
        cqi = 10,
        pci = 100,
        earfcn = 1300,
        cellId = 111L,
        tac = 5,
        mcc = "208",
        mnc = "10",
        operatorName = "TestOperator",
        connectionState = "REGISTERED",
        signalQuality = quality,
    )

    @Test
    fun `creating a session and inserting measurements is queryable`() = runTest {
        val sessionId = repository.createSession(startTime = 1000)
        repository.insertMeasurement(measurement(sessionId, 1000, -87, SignalQuality.GOOD))
        repository.insertMeasurement(measurement(sessionId, 2000, -95, SignalQuality.FAIR))

        val session = repository.getSession(sessionId)
        assertEquals(sessionId, session?.id)

        val page = repository.getMeasurementsPage(sessionId, limit = 10, offset = 0)
        assertEquals(2, page.size)
    }

    @Test
    fun `session aggregate computes min max avg and quality distribution`() = runTest {
        val sessionId = repository.createSession(startTime = 0)
        repository.insertMeasurements(
            listOf(
                measurement(sessionId, 0, -80, SignalQuality.EXCELLENT),
                measurement(sessionId, 1, -85, SignalQuality.GOOD),
                measurement(sessionId, 2, -95, SignalQuality.FAIR),
                measurement(sessionId, 3, null, SignalQuality.UNKNOWN),
            ),
        )

        val aggregate = repository.getSessionAggregate(sessionId)

        assertEquals(4, aggregate.totalCount)
        assertEquals(-95, aggregate.minRsrpDbm)
        assertEquals(-80, aggregate.maxRsrpDbm)
        assertEquals(1, aggregate.countExcellent)
        assertEquals(1, aggregate.countGood)
        assertEquals(1, aggregate.countFair)
        assertEquals(1, aggregate.countUnknown)
    }

    @Test
    fun `getMeasurementsForDisplay filters by network type`() = runTest {
        val sessionId = repository.createSession(startTime = 0)
        repository.insertMeasurements(
            listOf(
                measurement(sessionId, 0, -80, SignalQuality.EXCELLENT, RanType.LTE),
                measurement(sessionId, 1, -80, SignalQuality.EXCELLENT, RanType.NR),
                measurement(sessionId, 2, -80, SignalQuality.EXCELLENT, RanType.GSM),
            ),
        )

        val lteOnly = repository.getMeasurementsForDisplay(sessionId, NetworkFilter.LTE_ONLY)
        assertEquals(1, lteOnly.size)
        assertEquals(RanType.LTE, lteOnly.single().networkType)

        val all = repository.getMeasurementsForDisplay(sessionId, NetworkFilter.ALL)
        assertEquals(3, all.size)
    }

    @Test
    fun `deleting a session cascades to its measurements`() = runTest {
        val sessionId = repository.createSession(startTime = 0)
        repository.insertMeasurement(measurement(sessionId, 0, -80, SignalQuality.EXCELLENT))

        repository.deleteSession(sessionId)

        assertNull(repository.getSession(sessionId))
        assertTrue(repository.getMeasurementsPage(sessionId, 10, 0).isEmpty())
    }

    @Test
    fun `deleteAllData clears every session and measurement`() = runTest {
        val sessionA = repository.createSession(startTime = 0)
        val sessionB = repository.createSession(startTime = 1)
        repository.insertMeasurement(measurement(sessionA, 0, -80, SignalQuality.EXCELLENT))
        repository.insertMeasurement(measurement(sessionB, 0, -80, SignalQuality.EXCELLENT))

        repository.deleteAllData()

        assertTrue(repository.getMeasurementsPage(sessionA, 10, 0).isEmpty())
        assertTrue(repository.getMeasurementsPage(sessionB, 10, 0).isEmpty())
        assertNull(repository.getSession(sessionA))
    }
}
