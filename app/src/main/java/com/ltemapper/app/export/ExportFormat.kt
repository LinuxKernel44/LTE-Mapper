package com.ltemapper.app.export

enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    CSV("CSV", "text/csv", "csv"),
    GEOJSON("GeoJSON", "application/geo+json", "geojson"),
    KML("KML", "application/vnd.google-earth.kml+xml", "kml"),
}
