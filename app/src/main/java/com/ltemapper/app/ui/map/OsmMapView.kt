package com.ltemapper.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.LocationSample
import com.ltemapper.app.domain.model.MapDisplayMode
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.mapping.HeatmapRenderer
import com.ltemapper.app.mapping.MapPoint
import com.ltemapper.app.mapping.OsmMapConfig
import com.ltemapper.app.mapping.PointsOverlay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

/**
 * osmdroid map bridged into Compose.
 *
 * The important detail here is what this composable *doesn't* do on every recomposition. An
 * `AndroidView(update = …)` block runs on each recomposition, and this screen recomposes several
 * times per second (every telephony push updates the dashboard). Doing the following work there
 * made the map flicker continuously:
 *
 *  - `MapView.setTileSource()` calls `MapTileProviderBase.clearTileCache()` internally, so the
 *    entire in-memory tile cache was being wiped and every visible tile re-decoded, many times a
 *    second. This was the main cause of the flicker.
 *  - clearing and rebuilding `overlays` allocated a fresh `Marker` each pass and briefly left the
 *    overlay list empty mid-frame.
 *  - `controller.animateTo()` restarted a camera animation on every pass, so the map kept
 *    twitching even when the position hadn't moved.
 *
 * Instead, the view and its overlays are created once and each piece of state is applied from its
 * own keyed [LaunchedEffect], so work happens only when that particular input actually changes.
 */
@Composable
fun OsmMapView(
    points: List<MapPoint>,
    displayMode: MapDisplayMode,
    tileSource: MapTileSource,
    customTileSource: CustomTileSourceConfig,
    followUser: Boolean,
    currentLocation: LocationSample?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pointsOverlay = remember { PointsOverlay(emptyList()) }
    val heatmapOverlay = remember { HeatmapRenderer(emptyList()) }
    val pathPolyline = remember { Polyline().apply { outlinePaint.strokeWidth = 8f } }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
        }
    }
    val locationMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "You are here"
            isEnabled = false
        }
    }

    // Overlays are attached once, in a fixed order, and afterwards only toggled/updated in place.
    val overlaysAttached = remember { mutableStateOf(false) }
    if (!overlaysAttached.value) {
        mapView.overlays.add(pointsOverlay)
        mapView.overlays.add(heatmapOverlay)
        mapView.overlays.add(pathPolyline)
        mapView.overlays.add(locationMarker)
        overlaysAttached.value = true
    }

    val centeredOnce = remember { mutableStateOf(false) }

    // Only touch the tile source when it genuinely changes — this call clears the tile cache.
    LaunchedEffect(tileSource, customTileSource) {
        mapView.setTileSource(OsmMapConfig.tileSourceFor(tileSource, customTileSource))
        mapView.maxZoomLevel = tileSource.maxZoom.toDouble()
    }

    // Switching modes just enables the matching overlay; nothing is allocated or removed.
    LaunchedEffect(displayMode) {
        pointsOverlay.isEnabled = displayMode == MapDisplayMode.POINTS
        heatmapOverlay.isEnabled = displayMode == MapDisplayMode.HEATMAP
        pathPolyline.isEnabled = displayMode == MapDisplayMode.PATH
        mapView.invalidate()
    }

    LaunchedEffect(points, displayMode) {
        when (displayMode) {
            MapDisplayMode.POINTS -> pointsOverlay.setPoints(points)
            MapDisplayMode.HEATMAP -> heatmapOverlay.setPoints(points)
            MapDisplayMode.PATH -> pathPolyline.setPoints(
                points.sortedBy { it.timestampMillis }.map { GeoPoint(it.latitude, it.longitude) },
            )
        }
        mapView.invalidate()
    }

    LaunchedEffect(currentLocation, followUser) {
        val location = currentLocation
        if (location == null) {
            locationMarker.isEnabled = false
            mapView.invalidate()
            return@LaunchedEffect
        }

        val here = GeoPoint(location.latitude, location.longitude)
        locationMarker.position = here
        locationMarker.isEnabled = true

        if (!centeredOnce.value) {
            mapView.controller.setCenter(here)
            centeredOnce.value = true
        } else if (followUser && mapView.hasMovedMeaningfullyFrom(here)) {
            // Guarded so a GPS fix that barely changed doesn't restart a camera animation and
            // make the map twitch.
            mapView.controller.animateTo(here)
        }
        mapView.invalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // No update block on purpose: every input is applied by the keyed effects above, so a
    // recomposition caused by unrelated state (a new RSRP reading, say) costs nothing here.
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** ~1.1 m at the equator — below this, re-centering is imperceptible and only causes jitter. */
private const val MEANINGFUL_MOVE_DEGREES = 1e-5

private fun MapView.hasMovedMeaningfullyFrom(target: GeoPoint): Boolean {
    val center = mapCenter
    return abs(center.latitude - target.latitude) > MEANINGFUL_MOVE_DEGREES ||
        abs(center.longitude - target.longitude) > MEANINGFUL_MOVE_DEGREES
}
