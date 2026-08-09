package com.ltemapper.app.domain.model

/**
 * Which tile server the map draws from — and, critically, downloads from.
 *
 * osmdroid caches tiles keyed by tile-source *name*, so offline downloads only ever show up if
 * the map is displaying the same source they were fetched for. Both the map and the offline
 * downloader therefore read this single setting.
 *
 * [supportsBulkDownload] mirrors the server operator's own policy, which osmdroid enforces in
 * code: the public OpenStreetMap server (Mapnik) forbids bulk downloading outright, so offline
 * areas simply cannot be pre-fetched from it. Pick a bulk-friendly source to download offline maps.
 */
enum class MapTileSource(
    val label: String,
    val supportsBulkDownload: Boolean,
    val minZoom: Int,
    val maxZoom: Int,
    val description: String,
    /** Shown on the map; displaying it is a condition of using these tile servers. */
    val attribution: String,
) {
    MAPNIK(
        label = "OpenStreetMap (standard)",
        supportsBulkDownload = false,
        minZoom = 0,
        maxZoom = 19,
        description = "Best for browsing. The public OSM server forbids bulk downloads, so offline areas can't be pre-downloaded from it.",
        attribution = "© OpenStreetMap contributors",
    ),
    OPEN_TOPO(
        label = "OpenTopoMap",
        supportsBulkDownload = true,
        minZoom = 0,
        maxZoom = 17,
        description = "Topographic style. Allows offline pre-downloading — please keep downloads reasonable, it's a volunteer-run service.",
        attribution = "© OpenStreetMap contributors, SRTM · © OpenTopoMap (CC-BY-SA)",
    ),
    USGS_TOPO(
        label = "USGS Topo (US only)",
        supportsBulkDownload = true,
        minZoom = 0,
        maxZoom = 15,
        description = "US government topographic tiles. Allows offline pre-downloading. Only covers the United States.",
        attribution = "© USGS The National Map",
    ),
    CUSTOM(
        label = "Custom provider (API key)",
        supportsBulkDownload = true,
        minZoom = 0,
        maxZoom = 19,
        description = "Your own XYZ tile provider (MapTiler, Thunderforest, Stadia…). Recommended for downloading large areas like a whole country, within your plan's quota.",
        attribution = "© your tile provider · © OpenStreetMap contributors",
    ),
    ;

    val isCustom: Boolean get() = this == CUSTOM
}

/** User-supplied XYZ endpoint used when [MapTileSource.CUSTOM] is selected. */
data class CustomTileSourceConfig(
    val urlTemplate: String = "",
    val apiKey: String = "",
) {
    /** Expects an XYZ template such as `https://api.example.com/tiles/{z}/{x}/{y}.png?key={apiKey}`. */
    val isValid: Boolean
        get() = urlTemplate.contains("{z}") && urlTemplate.contains("{x}") && urlTemplate.contains("{y}") &&
            urlTemplate.startsWith("https://")
}
