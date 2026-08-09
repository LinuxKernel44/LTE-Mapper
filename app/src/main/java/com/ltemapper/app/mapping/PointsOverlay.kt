package com.ltemapper.app.mapping

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Draws every recorded [MapPoint] as a small filled, colored dot — "point mode". Rendering
 * thousands of dots in a single [Overlay.draw] pass is dramatically cheaper than one osmdroid
 * [org.osmdroid.views.overlay.Marker] per point.
 *
 * [draw] is on the frame path, so it allocates nothing and skips points outside the viewport
 * before projecting them.
 */
class PointsOverlay(private var points: List<MapPoint>) : Overlay() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }
    private val lowAccuracyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(180, 0, 0, 0)
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }

    private val screenPoint = Point()
    private val reusableGeoPoint = GeoPoint(0.0, 0.0)

    fun setPoints(newPoints: List<MapPoint>) {
        points = newPoints
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return

        val projection = mapView.projection
        val bounds = mapView.boundingBox
        val latMargin = bounds.latitudeSpan * VIEWPORT_MARGIN_FRACTION
        val lonMargin = bounds.longitudeSpan * VIEWPORT_MARGIN_FRACTION

        for (point in points) {
            if (point.latitude < bounds.latSouth - latMargin || point.latitude > bounds.latNorth + latMargin) continue
            if (point.longitude < bounds.lonWest - lonMargin || point.longitude > bounds.lonEast + lonMargin) continue

            reusableGeoPoint.setCoords(point.latitude, point.longitude)
            projection.toPixels(reusableGeoPoint, screenPoint)

            val x = screenPoint.x.toFloat()
            val y = screenPoint.y.toFloat()
            fillPaint.color = SignalColor.androidArgb(point.quality)
            canvas.drawCircle(x, y, RADIUS_PX, fillPaint)
            // Low-accuracy fixes get a dashed outline so they're distinguishable from trusted
            // ones without relying on color.
            canvas.drawCircle(x, y, RADIUS_PX, if (point.isLowAccuracy) lowAccuracyStrokePaint else strokePaint)
        }
    }

    private companion object {
        const val RADIUS_PX = 7f
        const val VIEWPORT_MARGIN_FRACTION = 0.1
    }
}
