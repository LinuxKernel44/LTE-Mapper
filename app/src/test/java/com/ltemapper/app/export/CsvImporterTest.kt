package com.ltemapper.app.export

import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.usecase.ClassifySignalUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvImporterTest {

    private val repository = FakeMappingRepository()
    private val importer = CsvImporter(ClassifySignalUseCase(), repository)

    @Test
    fun `parses a well formed export`() = runTest {
        val csv = buildString {
            appendLine(CsvFormat.HEADER.joinToString(","))
            appendLine("2026-08-08T15:30:12Z,43.1234,2.5678,4.2,LTE,,-87,-10,18,,142,1300,12345678,5,208,10,Orange,GOOD,false")
        }

        val result = importer.import(csv.byteStream())

        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)
        val m = repository.measurements.single()
        assertEquals(43.1234, m.latitude, 0.0001)
        assertEquals(RanType.LTE, m.networkType)
        assertEquals(-87, m.rsrpDbm)
        assertEquals(SignalQuality.GOOD, m.signalQuality)
        // The session row is created lazily by the importer and stamped with the real id.
        assertEquals(1, repository.sessions.size)
        assertEquals(repository.sessions.single().id, m.sessionId)
    }

    @Test
    fun `reordered columns still parse by header name`() = runTest {
        val csv = buildString {
            appendLine("latitude,longitude,timestamp,rsrp")
            appendLine("1.0,2.0,1000,-95")
        }

        val result = importer.import(csv.byteStream())

        assertEquals(1, result.importedCount)
        assertEquals(1.0, repository.measurements.single().latitude, 0.0001)
        // No explicit signal_quality column: falls back to classifying the rsrp value.
        assertEquals(SignalQuality.FAIR, repository.measurements.single().signalQuality)
    }

    @Test
    fun `malformed rows are skipped without crashing`() = runTest {
        val csv = buildString {
            appendLine("timestamp,latitude,longitude")
            appendLine("1000,1.0,2.0")
            appendLine("not-a-number,1.0,2.0")
            appendLine("1000,999,2.0") // latitude out of range
            appendLine("1000,1.0") // missing column
            appendLine("2000,3.0,4.0")
        }

        val result = importer.import(csv.byteStream())

        assertEquals(2, result.importedCount)
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun `missing required columns fails gracefully`() = runTest {
        val csv = "operator\nOrange\n"
        val result = importer.import(csv.byteStream())

        assertEquals(0, result.importedCount)
        assertTrue(result.errors.isNotEmpty())
        assertTrue("a file that can't be parsed at all must not create a phantom session", repository.sessions.isEmpty())
    }

    @Test
    fun `empty file fails gracefully`() = runTest {
        val result = importer.import("".byteStream())
        assertEquals(0, result.importedCount)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(repository.sessions.isEmpty())
    }

    @Test
    fun `a file with only malformed rows never creates a session`() = runTest {
        val csv = buildString {
            appendLine("timestamp,latitude,longitude")
            appendLine("not-a-number,1.0,2.0")
            appendLine("1000,999,2.0")
        }

        val result = importer.import(csv.byteStream())

        assertEquals(0, result.importedCount)
        assertEquals(2, result.skippedCount)
        assertTrue("no valid row means no session should ever be created", repository.sessions.isEmpty())
    }

    @Test
    fun `session start and end time come from the data, not wall-clock import time`() = runTest {
        val csv = buildString {
            appendLine("timestamp,latitude,longitude")
            appendLine("5000,1.0,2.0")
            appendLine("1000,1.1,2.1") // out of order on purpose
            appendLine("9000,1.2,2.2")
        }

        importer.import(csv.byteStream())

        val session = repository.sessions.single()
        assertEquals(1000L, session.startTime)
        assertEquals(9000L, session.endTime)
        assertTrue("duration must never be negative", (session.endTime ?: 0L) >= session.startTime)
    }

    private fun String.byteStream() = ByteArrayInputStream(toByteArray(Charsets.UTF_8))
}
