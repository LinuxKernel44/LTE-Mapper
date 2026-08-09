package com.ltemapper.app.domain.usecase

import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.SessionAggregateRaw
import com.ltemapper.app.domain.model.SignalQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateSessionStatsUseCaseTest {

    private val calculate = CalculateSessionStatsUseCase()
    private val session = MappingSession(id = 1, startTime = 0, endTime = 1000, distanceMeters = 100.0, measurementCount = 10)

    @Test
    fun `quality percentages sum to 100 over classified measurements`() {
        val raw = SessionAggregateRaw(
            totalCount = 10,
            minRsrpDbm = -110, maxRsrpDbm = -75, avgRsrpDbm = -90.0, medianRsrpDbm = -88,
            avgRsrqDb = -10.0, avgSnrDb = 15.0,
            countExcellent = 2, countGood = 3, countFair = 3, countWeak = 1, countVeryWeak = 1, countNoSignal = 0,
            countUnknown = 0,
            minLat = 1.0, maxLat = 2.0, minLon = 3.0, maxLon = 4.0,
        )

        val summary = calculate(session, raw)

        val total = summary.qualityDistributionPercent.values.sum()
        assertEquals(100.0, total, 0.001)
        assertEquals(20.0, summary.qualityDistributionPercent.getValue(SignalQuality.EXCELLENT), 0.001)
        assertEquals(30.0, summary.qualityDistributionPercent.getValue(SignalQuality.GOOD), 0.001)
        assertNotNull(summary.boundingBox)
    }

    @Test
    fun `unknown measurements are excluded from the percentage base`() {
        val raw = SessionAggregateRaw(
            totalCount = 10,
            minRsrpDbm = null, maxRsrpDbm = null, avgRsrpDbm = null, medianRsrpDbm = null,
            avgRsrqDb = null, avgSnrDb = null,
            countExcellent = 0, countGood = 0, countFair = 0, countWeak = 0, countVeryWeak = 0, countNoSignal = 5,
            countUnknown = 5,
            minLat = null, maxLat = null, minLon = null, maxLon = null,
        )

        val summary = calculate(session, raw)

        assertEquals(100.0, summary.qualityDistributionPercent.getValue(SignalQuality.NO_SIGNAL), 0.001)
        assertNull(summary.boundingBox)
    }

    @Test
    fun `no measurements yields zero percentages without dividing by zero`() {
        val raw = SessionAggregateRaw(
            totalCount = 0,
            minRsrpDbm = null, maxRsrpDbm = null, avgRsrpDbm = null, medianRsrpDbm = null,
            avgRsrqDb = null, avgSnrDb = null,
            countExcellent = 0, countGood = 0, countFair = 0, countWeak = 0, countVeryWeak = 0, countNoSignal = 0,
            countUnknown = 0,
            minLat = null, maxLat = null, minLon = null, maxLon = null,
        )

        val summary = calculate(session, raw)

        assertEquals(0.0, summary.qualityDistributionPercent.values.sum(), 0.001)
    }
}
