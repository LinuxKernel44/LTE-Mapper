package com.ltemapper.app.telephony

import com.ltemapper.app.domain.model.RanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.CellIdentityLteBuilder
import org.robolectric.shadows.CellIdentityNrBuilder
import org.robolectric.shadows.CellInfoLteBuilder
import org.robolectric.shadows.CellInfoNrBuilder
import org.robolectric.shadows.CellSignalStrengthLteBuilder
import org.robolectric.shadows.CellSignalStrengthNrBuilder

/**
 * android.telephony.CellInfo and its identity/strength companions have no public constructors —
 * apps only ever receive instances from TelephonyManager — so Robolectric's CellInfo*Builder
 * shadows are the only way to construct real ones off-device. Robolectric only ships builders for
 * LTE and NR (not GSM/WCDMA/CDMA), so those RATs aren't covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CellInfoParserTest {

    @Test
    fun `parses a registered LTE cell`() {
        val identity = CellIdentityLteBuilder.newBuilder()
            .setMcc("208").setMnc("10").setCi(12345678).setPci(142).setTac(5).setEarfcn(1300)
            .build()
        val strength = CellSignalStrengthLteBuilder.newBuilder()
            .setRssi(-70).setRsrp(-87).setRsrq(-10).setRssnr(18).setCqi(12)
            .build()
        val cell = CellInfoLteBuilder.newBuilder()
            .setRegistered(true).setCellIdentity(identity).setCellSignalStrength(strength)
            .build()

        val result = checkNotNull(CellInfoParser.parse(cell, subscriptionId = 1, simSlotIndex = 0, operatorName = "Orange"))

        assertEquals(RanType.LTE, result.networkType)
        assertEquals("REGISTERED", result.connectionState)
        assertEquals(true, result.isRegistered)
        assertEquals(-87, result.rsrpDbm)
        assertEquals(-10, result.rsrqDb)
        assertEquals(18, result.rssnrDb)
        assertEquals(12, result.cqi)
        assertEquals(142, result.pci)
        assertEquals(1300, result.earfcn)
        assertEquals(12345678L, result.cellId)
        assertEquals(5, result.tac)
        assertEquals("208", result.mcc)
        assertEquals("10", result.mnc)
        assertEquals("Orange", result.operatorName)
        assertEquals(1, result.subscriptionId)
        assertEquals(0, result.simSlotIndex)
    }

    @Test
    fun `unregistered LTE cell is tagged NEIGHBOR`() {
        val identity = CellIdentityLteBuilder.newBuilder().setMcc("208").setMnc("10").build()
        val strength = CellSignalStrengthLteBuilder.newBuilder().setRsrp(-100).build()
        val cell = CellInfoLteBuilder.newBuilder()
            .setRegistered(false).setCellIdentity(identity).setCellSignalStrength(strength)
            .build()

        val result = checkNotNull(CellInfoParser.parse(cell, null, null, null))

        assertEquals("NEIGHBOR", result.connectionState)
        assertFalse(result.isRegistered)
    }

    @Test
    fun `platform unavailable sentinels are surfaced as null, never as a fake value`() {
        val identity = CellIdentityLteBuilder.newBuilder()
            .setCi(Int.MAX_VALUE).setPci(Int.MAX_VALUE).setTac(Int.MAX_VALUE).setEarfcn(Int.MAX_VALUE)
            .build()
        val strength = CellSignalStrengthLteBuilder.newBuilder()
            .setRsrp(Int.MAX_VALUE).setRsrq(Int.MAX_VALUE).setRssnr(Int.MAX_VALUE).setCqi(Int.MAX_VALUE)
            .build()
        val cell = CellInfoLteBuilder.newBuilder()
            .setRegistered(true).setCellIdentity(identity).setCellSignalStrength(strength)
            .build()

        val result = checkNotNull(CellInfoParser.parse(cell, null, null, null))

        assertNull("sentinel cell id must not be surfaced as a fake value", result.cellId)
        assertNull(result.pci)
        assertNull(result.tac)
        assertNull(result.earfcn)
        assertNull(result.rsrpDbm)
        assertNull(result.rsrqDb)
        assertNull(result.rssnrDb)
        assertNull(result.cqi)
    }

    @Test
    fun `parses a registered NR cell, mapping SS metrics onto the shared rsrp-rsrq-rssnr fields`() {
        val identity = CellIdentityNrBuilder.newBuilder()
            .setMcc("208").setMnc("20").setNci(123456789L).setPci(99).setTac(7).setNrarfcn(650000)
            .build()
        val strength = CellSignalStrengthNrBuilder.newBuilder()
            .setSsRsrp(-95).setSsRsrq(-12).setSsSinr(20)
            .build()
        val cell = CellInfoNrBuilder.newBuilder()
            .setRegistered(true).setCellIdentity(identity).setCellSignalStrength(strength)
            .build()

        val result = checkNotNull(CellInfoParser.parse(cell, subscriptionId = 2, simSlotIndex = 1, operatorName = "SFR"))

        assertEquals(RanType.NR, result.networkType)
        assertEquals("REGISTERED", result.connectionState)
        // NR has no PLMN-level RSSI concept in this app's model — only the SS-* beam metrics,
        // surfaced through the shared rsrp/rsrq/rssnr fields (see CellInfoParser's class doc).
        assertEquals(-95, result.rsrpDbm)
        assertEquals(-12, result.rsrqDb)
        assertEquals(20, result.rssnrDb)
        assertEquals(99, result.pci)
        assertEquals(650000, result.earfcn)
        assertEquals(123456789L, result.cellId)
        assertEquals(7, result.tac)
        assertEquals("208", result.mcc)
        assertEquals("20", result.mnc)
        assertEquals("SFR", result.operatorName)
        assertEquals(2, result.subscriptionId)
        assertEquals(1, result.simSlotIndex)
    }
}
