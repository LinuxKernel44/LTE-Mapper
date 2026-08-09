package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** org.json is stubbed out (throws/returns defaults) under a plain JVM unit test — Robolectric is
 * needed here purely to get a real org.json implementation to parse the exporter's output back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeoJsonExporterTest {

    private fun measurement(id: Long, sessionId: Long, rsrp: Int? = -87) = Measurement(
        id = id,
        sessionId = sessionId,
        timestamp = 1_754_666_000_000,
        latitude = 43.1234,
        longitude = 2.5678,
        accuracyMeters = 4.2f,
        isLowAccuracy = false,
        networkType = RanType.LTE,
        rssiDbm = -70,
        rsrpDbm = rsrp,
        rsrqDb = -10,
        rssnrDb = 18,
        cqi = 12,
        pci = 142,
        earfcn = 1300,
        cellId = 12345678L,
        tac = 5,
        mcc = "208",
        mnc = "10",
        operatorName = "Orange",
        connectionState = "REGISTERED",
        signalQuality = SignalQuality.GOOD,
    )

    @Test
    fun `exports a valid FeatureCollection with one Feature per measurement`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1), measurement(2, sessionId = 1)))
        val exporter = GeoJsonExporter(repo)
        val output = ByteArrayOutputStream()

        val count = exporter.export(sessionId = 1, output)

        assertEquals(2, count)
        val json = JSONObject(output.toString(Charsets.UTF_8.name()))
        assertEquals("FeatureCollection", json.getString("type"))
        val features = json.getJSONArray("features")
        assertEquals(2, features.length())
    }

    @Test
    fun `coordinates are longitude, latitude order per the GeoJSON spec`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1)))
        val exporter = GeoJsonExporter(repo)
        val output = ByteArrayOutputStream()

        exporter.export(sessionId = 1, output)

        val feature = JSONObject(output.toString(Charsets.UTF_8.name())).getJSONArray("features").getJSONObject(0)
        val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(2.5678, coordinates.getDouble(0), 0.0001) // longitude first
        assertEquals(43.1234, coordinates.getDouble(1), 0.0001) // then latitude
    }

    @Test
    fun `null fields are emitted as JSON null rather than omitted or fabricated`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1, rsrp = null)))
        val exporter = GeoJsonExporter(repo)
        val output = ByteArrayOutputStream()

        exporter.export(sessionId = 1, output)

        val properties = JSONObject(output.toString(Charsets.UTF_8.name())).getJSONArray("features").getJSONObject(0).getJSONObject("properties")
        assertTrue(properties.has("rsrp"))
        assertTrue(properties.isNull("rsrp"))
    }

    @Test
    fun `only exports measurements for the requested session`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1), measurement(2, sessionId = 2)))
        val exporter = GeoJsonExporter(repo)
        val output = ByteArrayOutputStream()

        val count = exporter.export(sessionId = 1, output)

        assertEquals(1, count)
    }

    @Test
    fun `empty session exports a valid empty FeatureCollection`() = runTest {
        val repo = FakeMappingRepository()
        val exporter = GeoJsonExporter(repo)
        val output = ByteArrayOutputStream()

        val count = exporter.export(sessionId = 1, output)

        assertEquals(0, count)
        val json = JSONObject(output.toString(Charsets.UTF_8.name()))
        assertEquals(0, json.getJSONArray("features").length())
    }
}
