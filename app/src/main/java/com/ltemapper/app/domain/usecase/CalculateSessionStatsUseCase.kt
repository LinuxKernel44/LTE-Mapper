package com.ltemapper.app.domain.usecase

import com.ltemapper.app.domain.model.BoundingBox
import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.SessionAggregateRaw
import com.ltemapper.app.domain.model.SessionSummary
import com.ltemapper.app.domain.model.SignalQuality
import javax.inject.Inject

/**
 * Turns SQL-computed aggregates into a [SessionSummary]. Pure function so it can be unit tested
 * without a database — the expensive aggregation itself happens in SQL, not here, so this stays
 * cheap even for sessions with 100k+ measurements.
 */
class CalculateSessionStatsUseCase @Inject constructor() {
    operator fun invoke(session: MappingSession, raw: SessionAggregateRaw): SessionSummary {
        val classified = raw.totalCount - raw.countUnknown
        fun percent(count: Int): Double = if (classified <= 0) 0.0 else (count.toDouble() / classified) * 100.0

        val distribution = mapOf(
            SignalQuality.EXCELLENT to percent(raw.countExcellent),
            SignalQuality.GOOD to percent(raw.countGood),
            SignalQuality.FAIR to percent(raw.countFair),
            SignalQuality.WEAK to percent(raw.countWeak),
            SignalQuality.VERY_WEAK to percent(raw.countVeryWeak),
            SignalQuality.NO_SIGNAL to percent(raw.countNoSignal),
        )

        val bbox = if (raw.minLat != null && raw.maxLat != null && raw.minLon != null && raw.maxLon != null) {
            BoundingBox(raw.minLat, raw.maxLat, raw.minLon, raw.maxLon)
        } else null

        return SessionSummary(
            session = session,
            minRsrpDbm = raw.minRsrpDbm,
            maxRsrpDbm = raw.maxRsrpDbm,
            avgRsrpDbm = raw.avgRsrpDbm,
            medianRsrpDbm = raw.medianRsrpDbm,
            avgRsrqDb = raw.avgRsrqDb,
            avgSnrDb = raw.avgSnrDb,
            qualityDistributionPercent = distribution,
            boundingBox = bbox,
        )
    }
}
