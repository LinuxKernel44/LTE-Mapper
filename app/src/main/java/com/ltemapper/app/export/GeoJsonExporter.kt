package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.repository.MappingRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

/** Streams a session as a GeoJSON FeatureCollection, one Feature object built (and forgotten) at
 * a time so memory use stays flat regardless of session size. */
class GeoJsonExporter @Inject constructor(private val repository: MappingRepository) {

    suspend fun export(sessionId: Long, outputStream: OutputStream): Int {
        var written = 0
        BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
            writer.write("""{"type":"FeatureCollection","features":[""")
            var offset = 0
            var first = true
            while (true) {
                val page = repository.getMeasurementsPage(sessionId, PAGE_SIZE, offset)
                if (page.isEmpty()) break
                for (m in page) {
                    if (!first) writer.write(",")
                    first = false
                    writer.write(toFeature(m).toString())
                    written++
                }
                offset += page.size
                if (page.size < PAGE_SIZE) break
            }
            writer.write("]}")
            writer.flush()
        }
        return written
    }

    private fun toFeature(m: Measurement): JSONObject = JSONObject().apply {
        put("type", "Feature")
        put(
            "geometry",
            JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().put(m.longitude).put(m.latitude))
            },
        )
        put(
            "properties",
            JSONObject().apply {
                put("timestamp", m.timestamp)
                put("accuracy", m.accuracyMeters?.toDouble() ?: JSONObject.NULL)
                put("network_type", m.networkType.name)
                put("rssi", m.rssiDbm ?: JSONObject.NULL)
                put("rsrp", m.rsrpDbm ?: JSONObject.NULL)
                put("rsrq", m.rsrqDb ?: JSONObject.NULL)
                put("snr", m.rssnrDb ?: JSONObject.NULL)
                put("cqi", m.cqi ?: JSONObject.NULL)
                put("pci", m.pci ?: JSONObject.NULL)
                put("earfcn", m.earfcn ?: JSONObject.NULL)
                put("cell_id", m.cellId ?: JSONObject.NULL)
                put("tac", m.tac ?: JSONObject.NULL)
                put("mcc", m.mcc ?: JSONObject.NULL)
                put("mnc", m.mnc ?: JSONObject.NULL)
                put("operator", m.operatorName ?: JSONObject.NULL)
                put("signal_quality", m.signalQuality.name)
                put("is_low_accuracy", m.isLowAccuracy)
            },
        )
    }

    companion object {
        private const val PAGE_SIZE = 2000
    }
}
