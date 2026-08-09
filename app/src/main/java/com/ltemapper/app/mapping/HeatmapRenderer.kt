package com.ltemapper.app.mapping

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ltemapper.app.domain.model.SignalQuality
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Approximates a coverage heatmap by stacking soft radial gradients around each measured point —
 * "heatmap mode". This is a visual aid, not a true geostatistical interpolation (no kriging/IDW):
 * areas between sparse points are intentionally left blank rather than colored in, so the map
 * never implies precision the raw measurements don't support.
 *
 * [draw] runs on every frame, including while panning, so it allocates nothing: gradients are
 * built once per radius (there are only a handful of quality colors) and repositioned with a
 * local matrix, and off-screen points are skipped before any coordinate conversion. The previous
 * version allocated a `RadialGradient` and two `GeoPoint`s *per point per frame*, which caused
 * enough GC churn to make the map stutter.
 */
class HeatmapRenderer(private var points: List<MapPoint>, private var radiusMeters: Double = 40.0) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screenPoint = Point()
    private val reusableGeoPoint = GeoPoint(0.0, 0.0)
    private val shaderMatrix = Matrix()

    private val gradientCache = HashMap<SignalQuality, RadialGradient>()
    private var cachedRadiusPx = -1f

    fun setPoints(newPoints: List<MapPoint>) {
        points = newPoints
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return

        val projection = mapView.projection
        val metersPerPixel = projection.metersToEquatorPixels(1f).let { if (it <= 0f) 1f else it }
        val radiusPx = (radiusMeters * metersPerPixel).toFloat().coerceIn(12f, 220f)

        if (radiusPx != cachedRadiusPx) {
            gradientCache.clear()
            cachedRadiusPx = radiusPx
        }

        // Cull by geographic bounds first: cheaper than projecting every point, and a session can
        // hold thousands of them while only a few are on screen.
        val bounds = mapView.boundingBox
        val latMargin = bounds.latitudeSpan * VIEWPORT_MARGIN_FRACTION
        val lonMargin = bounds.longitudeSpan * VIEWPORT_MARGIN_FRACTION

        for (point in points) {
            if (point.latitude < bounds.latSouth - latMargin || point.latitude > bounds.latNorth + latMargin) continue
            if (point.longitude < bounds.lonWest - lonMargin || point.longitude > bounds.lonEast + lonMargin) continue

            reusableGeoPoint.setCoords(point.latitude, point.longitude)
            projection.toPixels(reusableGeoPoint, screenPoint)

            val gradient = gradientCache.getOrPut(point.quality) { buildGradient(point.quality, radiusPx) }
            shaderMatrix.setTranslate(screenPoint.x.toFloat(), screenPoint.y.toFloat())
            gradient.setLocalMatrix(shaderMatrix)
            paint.shader = gradient

            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radiusPx, paint)
        }
        paint.shader = null
    }

    /** Built at the origin so a translation matrix can move it to each point without rebuilding. */
    private fun buildGradient(quality: SignalQuality, radiusPx: Float): RadialGradient {
        val baseColor = SignalColor.androidArgb(quality)
        val inner = Color.argb(110, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        val outer = Color.argb(0, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        return RadialGradient(0f, 0f, radiusPx, intArrayOf(inner, outer), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }

    private companion object {
        /** Keeps points just off-screen drawn, so their glow doesn't pop in at the edges. */
        const val VIEWPORT_MARGIN_FRACTION = 0.2
    }
}
