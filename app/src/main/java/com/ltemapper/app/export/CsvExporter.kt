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
        val values = listOf(
            timestampIso,
            m.latitude.toString(),
            m.longitude.toString(),
            m.accuracyMeters?.toString().orEmpty(),
            m.networkType.name,
            m.rssiDbm?.toString().orEmpty(),
            m.rsrpDbm?.toString().orEmpty(),
            m.rsrqDb?.toString().orEmpty(),
            m.rssnrDb?.toString().orEmpty(),
            m.cqi?.toString().orEmpty(),
            m.pci?.toString().orEmpty(),
            m.earfcn?.toString().orEmpty(),
            m.cellId?.toString().orEmpty(),
            m.tac?.toString().orEmpty(),
            m.mcc.orEmpty(),
            m.mnc.orEmpty(),
            m.operatorName.orEmpty(),
            m.signalQuality.name,
            m.isLowAccuracy.toString(),
        )
        return values.joinToString(",") { CsvFormat.escape(it) }
    }

    companion object {
        private const val PAGE_SIZE = 2000
    }
}
