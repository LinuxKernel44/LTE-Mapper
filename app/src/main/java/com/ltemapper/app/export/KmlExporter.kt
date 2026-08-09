package com.ltemapper.app.export

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.mapping.SignalColor
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

/** Streams a session as KML, one Placemark per measurement, colored to match the in-app legend. */
class KmlExporter @Inject constructor(private val repository: MappingRepository) {

    suspend fun export(sessionId: Long, outputStream: OutputStream): Int {
        var written = 0
        BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
            writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.newLine()
            writer.write("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""")
            writer.newLine()
            for (quality in SignalQuality.entries) {
                writer.write(styleFor(quality))
                writer.newLine()
            }

            var offset = 0
            while (true) {
                val page = repository.getMeasurementsPage(sessionId, PAGE_SIZE, offset)
                if (page.isEmpty()) break
                for (m in page) {
                    writer.write(toPlacemark(m))
                    writer.newLine()
                    written++
                }
                offset += page.size
                if (page.size < PAGE_SIZE) break
            }

            writer.write("</Document></kml>")
            writer.flush()
        }
        return written
    }

    private fun styleFor(quality: SignalQuality): String {
        // KML color order is aabbggrr, opposite of the aarrggbb ints we use elsewhere.
        val argb = SignalColor.androidArgb(quality)
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val kmlColor = String.format("%02x%02x%02x%02x", a, b, g, r)
        return """<Style id="${quality.name}"><IconStyle><color>$kmlColor</color><scale>0.8</scale></IconStyle></Style>"""
    }

    private fun toPlacemark(m: Measurement): String {
        val name = "${m.signalQuality.label} (${m.rsrpDbm?.let { "$it dBm" } ?: "N/A"})"
        val description = buildString {
            append("Network: ${m.networkType.label}\n")
            append("RSRP: ${m.rsrpDbm ?: "N/A"} dBm, RSRQ: ${m.rsrqDb ?: "N/A"} dB, SNR: ${m.rssnrDb ?: "N/A"} dB\n")
            append("PCI: ${m.pci ?: "N/A"}, EARFCN: ${m.earfcn ?: "N/A"}, Cell ID: ${m.cellId ?: "N/A"}\n")
            append("Operator: ${m.operatorName ?: "N/A"}")
        }
        return """<Placemark><name>${xmlEscape(name)}</name><styleUrl>#${m.signalQuality.name}</styleUrl>""" +
            """<description>${xmlEscape(description)}</description>""" +
            """<Point><coordinates>${m.longitude},${m.latitude},0</coordinates></Point></Placemark>"""
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    companion object {
        private const val PAGE_SIZE = 2000
    }
}
