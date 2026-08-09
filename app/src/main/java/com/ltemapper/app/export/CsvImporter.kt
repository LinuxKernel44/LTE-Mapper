package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.usecase.ClassifySignalUseCase
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parses this app's own CSV export format back into [Measurement]s. Column order is not assumed
 * — a header row is required and columns are matched by name, so files with extra/reordered
 * columns still import. Any row that fails to parse is skipped and counted rather than aborting
 * or crashing the import.
 */
class CsvImporter @Inject constructor(private val classifySignal: ClassifySignalUseCase) {

    fun parse(inputStream: InputStream, targetSessionId: Long): ImportResult {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val headerLine = reader.readLine() ?: return ImportResult(emptyList(), 0, listOf("Empty file"))
        val header = CsvFormat.splitLine(headerLine).map { it.trim().lowercase() }
        val columnIndex = header.withIndex().associate { (i, name) -> name to i }

        val requiredColumns = listOf(CsvFormat.COL_TIMESTAMP, CsvFormat.COL_LATITUDE, CsvFormat.COL_LONGITUDE)
        val missing = requiredColumns.filterNot { columnIndex.containsKey(it) }
        if (missing.isNotEmpty()) {
            return ImportResult(emptyList(), 0, listOf("Missing required columns: ${missing.joinToString()}"))
        }

        val measurements = mutableListOf<Measurement>()
        val errors = mutableListOf<String>()
        var skipped = 0
        var lineNumber = 1

        reader.forEachLine { line ->
            lineNumber++
            if (line.isBlank()) return@forEachLine
            val fields = CsvFormat.splitLine(line)
            val measurement = runCatching { parseRow(fields, columnIndex, targetSessionId) }.getOrNull()
            if (measurement == null) {
                skipped++
                if (errors.size < MAX_ERRORS_KEPT) errors.add("Line $lineNumber: could not parse row")
            } else {
                measurements.add(measurement)
            }
        }

        return ImportResult(measurements, skipped, errors)
    }

    private fun parseRow(fields: List<String>, columnIndex: Map<String, Int>, sessionId: Long): Measurement? {
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
            sessionId = sessionId,
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
    }
}
