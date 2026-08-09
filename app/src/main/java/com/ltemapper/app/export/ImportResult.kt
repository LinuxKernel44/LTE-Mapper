package com.ltemapper.app.export

/** Rows are streamed straight into the repository as they're parsed (see [CsvImporter]) rather
 * than collected here, so a large import never holds the whole file in memory at once. */
data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String>,
)
