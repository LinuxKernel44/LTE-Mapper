package com.ltemapper.app.export

import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.usecase.ClassifySignalUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvImporterTest {

    private val importer = CsvImporter(ClassifySignalUseCase())

    @Test
    fun `parses a well formed export`() {
        val csv = buildString {
            appendLine(CsvFormat.HEADER.joinToString(","))
            appendLine("2026-08-08T15:30:12Z,43.1234,2.5678,4.2,LTE,,-87,-10,18,,142,1300,12345678,5,208,10,Orange,GOOD,false")
        }

        val result = importer.parse(csv.byteStream(), targetSessionId = 42)

        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)
        val m = result.measurements.single()
        assertEquals(42L, m.sessionId)
        assertEquals(43.1234, m.latitude, 0.0001)
        assertEquals(RanType.LTE, m.networkType)
        assertEquals(-87, m.rsrpDbm)
        assertEquals(SignalQuality.GOOD, m.signalQuality)
    }

    @Test
    fun `reordered columns still parse by header name`() {
        val csv = buildString {
            appendLine("latitude,longitude,timestamp,rsrp")
            appendLine("1.0,2.0,1000,-95")
        }

        val result = importer.parse(csv.byteStream(), targetSessionId = 1)

        assertEquals(1, result.importedCount)
        assertEquals(1.0, result.measurements.single().latitude, 0.0001)
        // No explicit signal_quality column: falls back to classifying the rsrp value.
        assertEquals(SignalQuality.FAIR, result.measurements.single().signalQuality)
    }

    @Test
    fun `malformed rows are skipped without crashing`() {
        val csv = buildString {
            appendLine("timestamp,latitude,longitude")
            appendLine("1000,1.0,2.0")
            appendLine("not-a-number,1.0,2.0")
            appendLine("1000,999,2.0") // latitude out of range
            appendLine("1000,1.0") // missing column
            appendLine("2000,3.0,4.0")
        }

        val result = importer.parse(csv.byteStream(), targetSessionId = 1)

        assertEquals(2, result.importedCount)
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun `missing required columns fails gracefully`() {
        val csv = "operator\nOrange\n"
        val result = importer.parse(csv.byteStream(), targetSessionId = 1)

        assertEquals(0, result.importedCount)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `empty file fails gracefully`() {
        val result = importer.parse("".byteStream(), targetSessionId = 1)
        assertEquals(0, result.importedCount)
        assertTrue(result.errors.isNotEmpty())
    }

    private fun String.byteStream() = ByteArrayInputStream(toByteArray(Charsets.UTF_8))
}
