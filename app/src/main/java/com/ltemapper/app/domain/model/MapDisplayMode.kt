package com.ltemapper.app.domain.model

enum class MapDisplayMode(val label: String) {
    POINTS("Points"),
    HEATMAP("Heatmap"),
    PATH("Path"),
}

// Map styling is expressed by MapTileSource: the displayed tiles and the downloadable tiles must
// be one and the same setting, since osmdroid caches per tile-source name.
