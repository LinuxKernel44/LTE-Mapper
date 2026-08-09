package com.ltemapper.app.ui.map

import app.cash.turbine.test
import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.RanType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The map used to flicker partly because every modem report — most of them identical apart from
 * their timestamp — pushed a fresh UI state and re-ran the map's update logic. These tests cover
 * the two flow behaviours that fix it, in isolation from Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelEmissionTest {

    private fun signal(rsrp: Int, timestamp: Long) = CellSignalInfo(
        networkType = RanType.LTE,
        connectionState = "REGISTERED",
        isRegistered = true,
        rsrpDbm = rsrp,
        timestamp = timestamp,
    )

    /** Mirrors the operator used by MapViewModel.liveTelephony. */
    private fun Flow<CellSignalInfo>.dropTimestampOnlyChanges(): Flow<CellSignalInfo> =
        distinctUntilChanged { old, new -> old.copy(timestamp = 0) == new.copy(timestamp = 0) }

    @Test
    fun `readings that differ only by timestamp are collapsed`() = runTest {
        val upstream = MutableSharedFlow<CellSignalInfo>(replay = 0, extraBufferCapacity = 16)

        upstream.dropTimestampOnlyChanges().test {
            upstream.emit(signal(rsrp = -100, timestamp = 1_000))
            assertEquals(-100, awaitItem().rsrpDbm)

            // Same reading re-reported three times: none of these should reach the UI.
            upstream.emit(signal(rsrp = -100, timestamp = 2_000))
            upstream.emit(signal(rsrp = -100, timestamp = 3_000))
            upstream.emit(signal(rsrp = -100, timestamp = 4_000))
            expectNoEvents()

            // A genuine change still gets through.
            upstream.emit(signal(rsrp = -95, timestamp = 5_000))
            assertEquals(-95, awaitItem().rsrpDbm)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seeding with null lets the screen render before the first fix arrives`() = runTest {
        // A source that never emits — a phone indoors with no GPS fix, or telephony access denied.
        val neverEmits = MutableSharedFlow<CellSignalInfo>()

        neverEmits.onStart<CellSignalInfo?> { emit(null) }.test {
            // Without the seed, combine() in the ViewModel would never produce a UI state at all
            // and the whole map screen would stay frozen on its defaults.
            assertNull(awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the seed does not suppress the first real reading`() = runTest {
        val upstream = MutableSharedFlow<CellSignalInfo>(replay = 0, extraBufferCapacity = 16)

        upstream.dropTimestampOnlyChanges().onStart<CellSignalInfo?> { emit(null) }.test {
            assertNull(awaitItem())
            upstream.emit(signal(rsrp = -88, timestamp = 1_000))
            assertEquals(-88, awaitItem()?.rsrpDbm)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
