package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CsvExporterTest {

    private fun measurement(id: Long, sessionId: Long, timestamp: Long, operator: String? = null) = Measurement(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
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
    fun `exports header and one row per measurement`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1, timestamp = 1_754_666_000_000), measurement(2, sessionId = 1, timestamp = 1_754_666_001_000)))
        val exporter = CsvExporter(repo)
        val output = ByteArrayOutputStream()

        val count = exporter.export(sessionId = 1, output)

        val lines = output.toString(Charsets.UTF_8.name()).trim().lines()
        assertEquals(2, count)
        assertEquals(3, lines.size) // header + 2 rows
        assertEquals(CsvFormat.HEADER.joinToString(","), lines[0])
        assertTrue(lines[1].contains("-87"))
    }

    @Test
    fun `only exports measurements for the requested session`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1, timestamp = 0), measurement(2, sessionId = 2, timestamp = 0)))
        val exporter = CsvExporter(repo)
        val output = ByteArrayOutputStream()

        val count = exporter.export(sessionId = 1, output)

        assertEquals(1, count)
    }

    @Test
    fun `operator names containing commas are quoted`() = runTest {
        val repo = FakeMappingRepository(listOf(measurement(1, sessionId = 1, timestamp = 0, operator = "Orange, S.A.")))
        val exporter = CsvExporter(repo)
        val output = ByteArrayOutputStream()

        exporter.export(sessionId = 1, output)

        val dataLine = output.toString(Charsets.UTF_8.name()).trim().lines()[1]
        assertEquals(listOf("Orange, S.A."), CsvFormat.splitLine(dataLine).filter { it == "Orange, S.A." })
    }
}
