package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class KmlExporterTest {

    private fun measurement(id: Long, sessionId: Long, operator: String? = "Orange") = Measurement(
        id = id,
        sessionId = sessionId,
        timestamp = 1_754_666_000_000,
        latitude = 43.1234,
        longitude = 2.5678,
        accuracyMeters = 4.2f,
        isLowAccuracy = false,
        networkType = RanType.LTE,
        rssiDbm = -70,
        rsrpDbm = -87,
        rsrqDb = -10,
        rssnrDb = 18,
        cqi = 12,
        pci = 142,
        earfcn = 1300,
        cellId = 12345678L,
        tac = 5,
        mcc = "208",
        mnc = "10",
        operatorName = operator,
        connectionState = "REGISTERED",
        signalQuality = SignalQuality.GOOD,
    )

    @Test
    fun `exports a well formed KML document with one Placemark per measurement`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1), measurement(2, sessionId = 1)))
        val exporter = KmlExporter(repo)
        val output = ByteArrayOutputStream()

        val count = exporter.export(sessionId = 1, output)

        val xml = output.toString(Charsets.UTF_8.name())
        assertEquals(2, count)
        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("<kml"))
        assertTrue(xml.trim().endsWith("</Document></kml>"))
        assertEquals(2, Regex("<Placemark>").findAll(xml).count())
        // One <Style> per SignalQuality value, regardless of how many measurements were exported.
        assertEquals(SignalQuality.entries.size, Regex("<Style ").findAll(xml).count())
    }

    @Test
    fun `coordinates are longitude, latitude, altitude order per the KML spec`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1)))
        val exporter = KmlExporter(repo)
        val output = ByteArrayOutputStream()

        exporter.export(sessionId = 1, output)

        val xml = output.toString(Charsets.UTF_8.name())
        assertTrue(xml.contains("<coordinates>2.5678,43.1234,0</coordinates>"))
    }

    @Test
    fun `operator names with XML special characters are escaped, not injected as markup`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1, operator = "Orange & <Test> \"Co\"")))
        val exporter = KmlExporter(repo)
        val output = ByteArrayOutputStream()

        exporter.export(sessionId = 1, output)

        val xml = output.toString(Charsets.UTF_8.name())
        assertTrue(xml.contains("Orange &amp; &lt;Test&gt; &quot;Co&quot;"))
        assertTrue("raw, unescaped tag must never appear in the description text", !xml.contains("<Test>"))
    }

    @Test
    fun `only exports measurements for the requested session`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1), measurement(2, sessionId = 2)))
        val exporter = KmlExporter(repo)
        val output = ByteArrayOutputStream()

        val count = exporter.export(sessionId = 1, output)

        assertEquals(1, count)
    }
}
