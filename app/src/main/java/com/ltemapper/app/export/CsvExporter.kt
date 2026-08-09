package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.repository.MappingRepository
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Streams a session's measurements to CSV a page at a time so exporting 100k+ rows never holds
 * the whole session in memory. */
class CsvExporter @Inject constructor(private val repository: MappingRepository) {

    suspend fun export(sessionId: Long, outputStream: OutputStream): Int {
        var written = 0
        BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
            writer.write(CsvFormat.HEADER.joinToString(","))
            writer.newLine()

            var offset = 0
            while (true) {
                val page = repository.getMeasurementsPage(sessionId, PAGE_SIZE, offset)
                if (page.isEmpty()) break
                for (m in page) {
                    writer.write(toCsvRow(m))
                    writer.newLine()
                    written++
                }
                offset += page.size
                if (page.size < PAGE_SIZE) break
            }
            writer.flush()
        }
        return written
    }

    private fun toCsvRow(m: Measurement): String {
        val timestampIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(m.timestamp).truncatedTo(ChronoUnit.SECONDS))
        // operatorName is broadcast by the cell tower, not trusted input, so — unlike every other
        // column here — it goes through the formula-injection guard on top of ordinary CSV
        // escaping (see CsvFormat.escapeUntrustedText).
        val values = listOf(
            CsvFormat.escape(timestampIso),
            CsvFormat.escape(m.latitude.toString()),
            CsvFormat.escape(m.longitude.toString()),
            CsvFormat.escape(m.accuracyMeters?.toString().orEmpty()),
            CsvFormat.escape(m.networkType.name),
            CsvFormat.escape(m.rssiDbm?.toString().orEmpty()),
            CsvFormat.escape(m.rsrpDbm?.toString().orEmpty()),
            CsvFormat.escape(m.rsrqDb?.toString().orEmpty()),
            CsvFormat.escape(m.rssnrDb?.toString().orEmpty()),
            CsvFormat.escape(m.cqi?.toString().orEmpty()),
            CsvFormat.escape(m.pci?.toString().orEmpty()),
            CsvFormat.escape(m.earfcn?.toString().orEmpty()),
            CsvFormat.escape(m.cellId?.toString().orEmpty()),
            CsvFormat.escape(m.tac?.toString().orEmpty()),
            CsvFormat.escape(m.mcc.orEmpty()),
            CsvFormat.escape(m.mnc.orEmpty()),
            CsvFormat.escapeUntrustedText(m.operatorName.orEmpty()),
            CsvFormat.escape(m.signalQuality.name),
            CsvFormat.escape(m.isLowAccuracy.toString()),
        )
        return values.joinToString(",")
    }

    companion object {
        private const val PAGE_SIZE = 2000
    }
}
