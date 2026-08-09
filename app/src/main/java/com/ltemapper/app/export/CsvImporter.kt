package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.domain.usecase.ClassifySignalUseCase
import com.ltemapper.app.location.LocationFilter
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parses this app's own CSV export format back into [Measurement]s and streams them straight into
 * the repository in bounded batches, so importing a very large file (100k+ rows) never holds the
 * whole thing in memory at once — mirroring how [CsvExporter] streams pages out rather than
 * building one giant list. Column order is not assumed — a header row is required and columns are
 * matched by name, so files with extra/reordered columns still import. Any row that fails to
 * parse is skipped and counted rather than aborting or crashing the import.
 *
 * The session row is created lazily, only once the first row parses successfully, so a malformed
 * or empty file never leaves behind an empty session stuck at "in progress" forever. Distance and
 * timestamp bounds are tracked as running totals while streaming (in file order, the same way the
 * live mapper itself accumulates distance) and applied to the session once the scan finishes.
 */
class CsvImporter @Inject constructor(
    private val classifySignal: ClassifySignalUseCase,
    private val repository: MappingRepository,
) {

    suspend fun import(inputStream: InputStream): ImportResult {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val headerLine = reader.readLine() ?: return ImportResult(0, 0, listOf("Empty file"))
        val header = CsvFormat.splitLine(headerLine).map { it.trim().lowercase() }
        val columnIndex = header.withIndex().associate { (i, name) -> name to i }

        val requiredColumns = listOf(CsvFormat.COL_TIMESTAMP, CsvFormat.COL_LATITUDE, CsvFormat.COL_LONGITUDE)
        val missing = requiredColumns.filterNot { columnIndex.containsKey(it) }
        if (missing.isNotEmpty()) {
            return ImportResult(0, 0, listOf("Missing required columns: ${missing.joinToString()}"))
        }

        var sessionId: Long? = null
        var imported = 0
        var skipped = 0
        var lineNumber = 1
        var minTimestamp = Long.MAX_VALUE
        var maxTimestamp = Long.MIN_VALUE
        var distance = 0.0
        var previous: Measurement? = null
        val errors = mutableListOf<String>()
        val batch = mutableListOf<Measurement>()

        suspend fun flushBatch() {
            if (batch.isEmpty()) return
            val id = sessionId ?: repository.createSession(batch.first().timestamp).also { sessionId = it }
            repository.importMeasurements(id, batch)
            batch.clear()
        }

        while (true) {
            val line = reader.readLine() ?: break
            lineNumber++
            if (line.isBlank()) continue
            val fields = CsvFormat.splitLine(line)
            val measurement = runCatching { parseRow(fields, columnIndex) }.getOrNull()
            if (measurement == null) {
                skipped++
                if (errors.size < MAX_ERRORS_KEPT) errors.add("Line $lineNumber: could not parse row")
                continue
            }

            imported++
            if (measurement.timestamp < minTimestamp) minTimestamp = measurement.timestamp
            if (measurement.timestamp > maxTimestamp) maxTimestamp = measurement.timestamp
            previous?.let {
                distance += LocationFilter.haversineMeters(it.latitude, it.longitude, measurement.latitude, measurement.longitude)
            }
            previous = measurement

            batch.add(measurement)
            if (batch.size >= BATCH_SIZE) flushBatch()
        }
        flushBatch()

        sessionId?.let { id -> repository.finalizeImportedSession(id, minTimestamp, maxTimestamp, distance) }

        return ImportResult(imported, skipped, errors)
    }

    private fun parseRow(fields: List<String>, columnIndex: Map<String, Int>): Measurement? {
        fun field(name: String): String? = columnIndex[name]?.let { fields.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

        val timestamp = parseTimestamp(field(CsvFormat.COL_TIMESTAMP)) ?: return null
        val latitude = field(CsvFormat.COL_LATITUDE)?.toDoubleOrNull() ?: return null
        val longitude = field(CsvFormat.COL_LONGITUDE)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        val rsrp = field(CsvFormat.COL_RSRP)?.toIntOrNull()
        val networkType = field(CsvFormat.COL_NETWORK_TYPE)?.let { name -> runCatching { RanType.valueOf(name) }.getOrNull() } ?: RanType.UNKNOWN
        val signalQuality = field(CsvFormat.COL_SIGNAL_QUALITY)?.let { name -> runCatching { SignalQuality.valueOf(name) }.getOrNull() }
            ?: classifySignal(rsrp)

        return Measurement(
            // Placeholder: overwritten with the real (lazily created) session id when the batch
            // containing this row is flushed — see MappingRepositoryImpl.importMeasurements.
            sessionId = 0L,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = field(CsvFormat.COL_ACCURACY)?.toFloatOrNull(),
            isLowAccuracy = field(CsvFormat.COL_LOW_ACCURACY)?.toBooleanStrictOrNull() ?: false,
            networkType = networkType,
            rssiDbm = field(CsvFormat.COL_RSSI)?.toIntOrNull(),
            rsrpDbm = rsrp,
            rsrqDb = field(CsvFormat.COL_RSRQ)?.toIntOrNull(),
            rssnrDb = field(CsvFormat.COL_SNR)?.toIntOrNull(),
            cqi = field(CsvFormat.COL_CQI)?.toIntOrNull(),
            pci = field(CsvFormat.COL_PCI)?.toIntOrNull(),
            earfcn = field(CsvFormat.COL_EARFCN)?.toIntOrNull(),
            cellId = field(CsvFormat.COL_CELL_ID)?.toLongOrNull(),
            tac = field(CsvFormat.COL_TAC)?.toIntOrNull(),
            mcc = field(CsvFormat.COL_MCC),
            mnc = field(CsvFormat.COL_MNC),
            operatorName = field(CsvFormat.COL_OPERATOR),
            connectionState = null,
            signalQuality = signalQuality,
        )
    }

    private fun parseTimestamp(raw: String?): Long? {
        if (raw == null) return null
        raw.toLongOrNull()?.let { return it }
        return runCatching { DateTimeFormatter.ISO_INSTANT.parse(raw, Instant::from).toEpochMilli() }.getOrNull()
    }

    companion object {
        private const val MAX_ERRORS_KEPT = 50
        private const val BATCH_SIZE = 2000
    }
}
