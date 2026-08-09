package com.ltemapper.app.export

/** Column order used by both [CsvExporter] and [CsvImporter]. Import matches by header name, not
 * position, so files with reordered or extra columns still work. */
object CsvFormat {
    const val COL_TIMESTAMP = "timestamp"
    const val COL_LATITUDE = "latitude"
    const val COL_LONGITUDE = "longitude"
    const val COL_ACCURACY = "accuracy"
    const val COL_NETWORK_TYPE = "network_type"
    const val COL_RSSI = "rssi"
    const val COL_RSRP = "rsrp"
    const val COL_RSRQ = "rsrq"
    const val COL_SNR = "snr"
    const val COL_CQI = "cqi"
    const val COL_PCI = "pci"
    const val COL_EARFCN = "earfcn"
    const val COL_CELL_ID = "cell_id"
    const val COL_TAC = "tac"
    const val COL_MCC = "mcc"
    const val COL_MNC = "mnc"
    const val COL_OPERATOR = "operator"
    const val COL_SIGNAL_QUALITY = "signal_quality"
    const val COL_LOW_ACCURACY = "is_low_accuracy"

    val HEADER = listOf(
        COL_TIMESTAMP, COL_LATITUDE, COL_LONGITUDE, COL_ACCURACY, COL_NETWORK_TYPE,
        COL_RSSI, COL_RSRP, COL_RSRQ, COL_SNR, COL_CQI, COL_PCI, COL_EARFCN, COL_CELL_ID,
        COL_TAC, COL_MCC, COL_MNC, COL_OPERATOR, COL_SIGNAL_QUALITY, COL_LOW_ACCURACY,
    )

    /** Leading characters Excel/Sheets/LibreOffice interpret as the start of a formula. */
    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@')

    fun escape(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuoting) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /** Same as [escape], but also neutralizes CSV/formula injection by prefixing a leading
     * apostrophe when the value starts with a formula-trigger character. Use this only for
     * free-text fields sourced from untrusted network broadcasts (operator name) — applying it to
     * numeric fields would corrupt legitimate negative dBm values, which all start with '-'. */
    fun escapeUntrustedText(value: String): String {
        val neutralized = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGER_CHARS) "'$value" else value
        return escape(neutralized)
    }

    /** Minimal RFC-4180-ish line splitter: handles quoted fields containing commas/escaped quotes. */
    fun splitLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
