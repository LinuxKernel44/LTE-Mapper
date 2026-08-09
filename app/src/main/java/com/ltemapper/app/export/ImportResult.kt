package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement

data class ImportResult(
    val measurements: List<Measurement>,
    val skippedCount: Int,
    val errors: List<String>,
) {
    val importedCount: Int get() = measurements.size
}
