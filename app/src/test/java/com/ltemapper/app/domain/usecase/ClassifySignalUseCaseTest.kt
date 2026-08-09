package com.ltemapper.app.domain.usecase

import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.model.SignalThresholds
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifySignalUseCaseTest {

    private val classify = ClassifySignalUseCase()
    private val thresholds = SignalThresholds.DEFAULT

    @Test
    fun `null rsrp is unknown`() {
        assertEquals(SignalQuality.UNKNOWN, classify(null, thresholds))
    }

    @Test
    fun `boundary values map to the higher bucket`() {
        assertEquals(SignalQuality.EXCELLENT, classify(-80, thresholds))
        assertEquals(SignalQuality.GOOD, classify(-90, thresholds))
        assertEquals(SignalQuality.FAIR, classify(-100, thresholds))
        assertEquals(SignalQuality.WEAK, classify(-110, thresholds))
    }

    @Test
    fun `values just above and below a boundary land in the correct bucket`() {
        assertEquals(SignalQuality.EXCELLENT, classify(-79, thresholds))
        assertEquals(SignalQuality.GOOD, classify(-81, thresholds))
        assertEquals(SignalQuality.FAIR, classify(-91, thresholds))
        assertEquals(SignalQuality.WEAK, classify(-101, thresholds))
        assertEquals(SignalQuality.VERY_WEAK, classify(-111, thresholds))
    }

    @Test
    fun `extremely low rsrp is no signal`() {
        assertEquals(SignalQuality.NO_SIGNAL, classify(-141, thresholds))
    }

    @Test
    fun `custom thresholds are respected`() {
        val custom = SignalThresholds(excellentMinDbm = -70, goodMinDbm = -85, fairMinDbm = -95, weakMinDbm = -105)
        assertEquals(SignalQuality.EXCELLENT, classify(-70, custom))
        assertEquals(SignalQuality.VERY_WEAK, classify(-106, custom))
    }
}
