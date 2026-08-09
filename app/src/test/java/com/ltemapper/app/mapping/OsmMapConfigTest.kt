package com.ltemapper.app.mapping

import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MapView.setTileSource()` clears the whole tile cache internally, so handing it a newly built
 * object when nothing changed makes the map visibly flash. These tests pin the memoization that
 * prevents that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class OsmMapConfigTest {

    private val noCustom = CustomTileSourceConfig()

    @Test
    fun `built-in sources return the same instance every time`() {
        MapTileSource.entries.filterNot { it.isCustom }.forEach { source ->
            assertSame(
                "Rebuilding $source would wipe the tile cache and flash the map",
                OsmMapConfig.tileSourceFor(source, noCustom),
                OsmMapConfig.tileSourceFor(source, noCustom),
            )
        }
    }

    @Test
    fun `an identical custom config returns the same instance`() {
        val config = CustomTileSourceConfig("https://tiles.example.com/{z}/{x}/{y}.png?k={apiKey}", "abc")
        assertSame(
            OsmMapConfig.tileSourceFor(MapTileSource.CUSTOM, config),
            OsmMapConfig.tileSourceFor(MapTileSource.CUSTOM, config.copy()),
        )
    }

    @Test
    fun `different custom configs return different instances`() {
        val a = CustomTileSourceConfig("https://a.example.com/{z}/{x}/{y}.png", "key")
        val b = CustomTileSourceConfig("https://b.example.com/{z}/{x}/{y}.png", "key")
        assertNotSame(
            OsmMapConfig.tileSourceFor(MapTileSource.CUSTOM, a),
            OsmMapConfig.tileSourceFor(MapTileSource.CUSTOM, b),
        )
    }

    @Test
    fun `an invalid custom config falls back instead of producing a broken source`() {
        val invalid = CustomTileSourceConfig("not-a-url", "key")
        assertSame(
            OsmMapConfig.tileSourceFor(MapTileSource.MAPNIK, noCustom),
            OsmMapConfig.tileSourceFor(MapTileSource.CUSTOM, invalid),
        )
    }
}
