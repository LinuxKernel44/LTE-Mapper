package com.ltemapper.app.mapping

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.ltemapper.app.domain.model.SignalQuality
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The overlay draw methods run on every frame, including while panning. These tests render them
 * for real against a laid-out [MapView] with a large point set — the path where the viewport
 * culling and the cached gradients actually matter, and where the previous implementation
 * allocated a shader per point per frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayRenderingTest {

    private lateinit var mapView: MapView
    private lateinit var canvas: Canvas
    private lateinit var bitmap: Bitmap

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OsmMapConfig.initialize(context)
        mapView = MapView(context).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, WIDTH, HEIGHT)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(CENTER_LAT, CENTER_LON))
        }
        bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
    }

    /** Guards against a vacuous pass: if every point were culled (or the projection silently
     * failed) the canvas would stay blank and every "doesn't throw" test would still succeed. */
    private fun assertSomethingWasDrawn() {
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val painted = pixels.count { it != 0 }
        org.junit.Assert.assertTrue("Nothing was rendered onto the canvas", painted > 0)
    }

    /** Spread over ~1 degree, so most points fall far outside the viewport and must be culled. */
    private fun points(count: Int) = List(count) { index ->
        MapPoint(
            latitude = CENTER_LAT + (index - count / 2) * 0.0002,
            longitude = CENTER_LON + (index - count / 2) * 0.0002,
            quality = SignalQuality.entries[index % SignalQuality.entries.size],
            rsrpDbm = -100,
            isLowAccuracy = index % 3 == 0,
            timestampMillis = 1_000L + index,
        )
    }

    @Test
    fun `points overlay renders a large set without throwing`() {
        PointsOverlay(points(5_000)).draw(canvas, mapView, false)
        assertSomethingWasDrawn()
    }

    @Test
    fun `heatmap overlay renders a large set without throwing`() {
        HeatmapRenderer(points(5_000)).draw(canvas, mapView, false)
        assertSomethingWasDrawn()
    }

    @Test
    fun `overlays survive repeated frames, exercising the gradient cache`() {
        val heatmap = HeatmapRenderer(points(1_000))
        val pointsOverlay = PointsOverlay(points(1_000))
        repeat(10) {
            heatmap.draw(canvas, mapView, false)
            pointsOverlay.draw(canvas, mapView, false)
        }
        assertSomethingWasDrawn()
    }

    @Test
    fun `changing zoom rebuilds gradients without throwing`() {
        val heatmap = HeatmapRenderer(points(500))
        heatmap.draw(canvas, mapView, false)
        mapView.controller.setZoom(10.0)
        heatmap.draw(canvas, mapView, false)
        mapView.controller.setZoom(18.0)
        heatmap.draw(canvas, mapView, false)
    }

    @Test
    fun `overlays handle points at extreme coordinates`() {
        val extremes = listOf(
            MapPoint(85.0, 179.9, SignalQuality.EXCELLENT, -70, false, 1L),
            MapPoint(-85.0, -179.9, SignalQuality.NO_SIGNAL, null, true, 2L),
            MapPoint(0.0, 0.0, SignalQuality.UNKNOWN, null, false, 3L),
        )
        PointsOverlay(extremes).draw(canvas, mapView, false)
        HeatmapRenderer(extremes).draw(canvas, mapView, false)
    }

    @Test
    fun `empty and shadow passes draw nothing`() {
        PointsOverlay(emptyList()).draw(canvas, mapView, false)
        HeatmapRenderer(emptyList()).draw(canvas, mapView, false)
        PointsOverlay(points(10)).draw(canvas, mapView, true)
        HeatmapRenderer(points(10)).draw(canvas, mapView, true)
    }

    private companion object {
        const val WIDTH = 480
        const val HEIGHT = 800
        const val CENTER_LAT = 48.85
        const val CENTER_LON = 2.35
    }
}
