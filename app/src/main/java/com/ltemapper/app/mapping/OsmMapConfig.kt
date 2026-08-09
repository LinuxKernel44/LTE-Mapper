package com.ltemapper.app.mapping

import android.content.Context
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

object OsmMapConfig {

    /** Must run once before any [MapView] is created. */
    fun initialize(context: Context) {
        val config = Configuration.getInstance()
        config.load(context, android.preference.PreferenceManager.getDefaultSharedPreferences(context))
        // Mapnik's policy requires a meaningful, app-specific user agent.
        config.userAgentValue = context.packageName

        // Offline maps live in filesDir, NOT cacheDir: Android is free to purge cacheDir under
        // storage pressure, which would silently erase areas the user deliberately downloaded.
        val tileRoot = context.filesDir.resolve("osmdroid")
        if (!tileRoot.exists()) tileRoot.mkdirs()
        config.osmdroidBasePath = tileRoot
        config.osmdroidTileCache = tileRoot.resolve("tiles").apply { if (!exists()) mkdirs() }

        // Default trim/max are sized for casual browsing; offline country downloads need headroom.
        config.tileFileSystemCacheMaxBytes = MAX_TILE_CACHE_BYTES
        config.tileFileSystemCacheTrimBytes = TRIM_TILE_CACHE_BYTES
    }

    /**
     * Returns a stable instance per configuration. Handing [MapView.setTileSource] a freshly
     * built object would wipe the tile cache (and flash the map) even when nothing changed, so
     * custom sources are memoized rather than reconstructed on each call.
     */
    fun tileSourceFor(source: MapTileSource, custom: CustomTileSourceConfig): ITileSource = when (source) {
        MapTileSource.MAPNIK -> TileSourceFactory.MAPNIK
        MapTileSource.OPEN_TOPO -> TileSourceFactory.OpenTopo
        MapTileSource.USGS_TOPO -> TileSourceFactory.USGS_TOPO
        MapTileSource.CUSTOM ->
            if (custom.isValid) customSourceCache.getOrPut(custom) { TemplatedTileSource(custom) } else TileSourceFactory.MAPNIK
    }

    private val customSourceCache = java.util.concurrent.ConcurrentHashMap<CustomTileSourceConfig, ITileSource>()

    /**
     * A user-configured XYZ endpoint (MapTiler, Thunderforest, Stadia, ...). Providers like these
     * explicitly permit offline caching within your plan's quota, which is what makes
     * country-scale downloads legitimate — unlike the public OSM server.
     */
    private class TemplatedTileSource(private val config: CustomTileSourceConfig) : OnlineTileSourceBase(
        // Cache is keyed by name, so derive it from the template: two different providers must
        // never share (and overwrite) each other's cached tiles.
        "custom-${config.urlTemplate.hashCode().toUInt().toString(16)}",
        MapTileSource.CUSTOM.minZoom,
        MapTileSource.CUSTOM.maxZoom,
        256,
        "",
        arrayOf(""),
        "© map data providers",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String = config.urlTemplate
            .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
            .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
            .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
            .replace("{apiKey}", config.apiKey)
    }

    private const val MAX_TILE_CACHE_BYTES = 4L * 1024 * 1024 * 1024
    private const val TRIM_TILE_CACHE_BYTES = 3L * 1024 * 1024 * 1024
}
