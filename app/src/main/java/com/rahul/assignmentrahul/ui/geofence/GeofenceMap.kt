package com.rahul.assignmentrahul.ui.geofence

import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rahul.assignmentrahul.data.model.Coordinates
import com.rahul.assignmentrahul.domain.GeoMath
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.log2

// Free OpenStreetMap-based vector tiles: no API key or billing account needed.
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val DEFAULT_CENTER = LatLng(20.5937, 78.9629) // India, until we know better
private const val DEFAULT_ZOOM = 4.0
private const val FOCUSED_ZOOM = 15.0

private const val GEOFENCE_SOURCE = "geofence-source"
private const val CIRCLE_STEPS = 64

/** Lets the screen move the camera without holding a reference to the map itself. */
class GeofenceMapController {
    private var map: MapLibreMap? = null
    private var pendingTarget: Coordinates? = null

    private var pendingRadius: Float? = null

    /** Centres on [target]; with [fitRadiusMeters] it zooms so that zone fills a good part of the screen. */
    fun moveTo(target: Coordinates, fitRadiusMeters: Float? = null) {
        val current = map
        if (current == null) {
            // Map not ready yet; apply once it is.
            pendingTarget = target
            pendingRadius = fitRadiusMeters
            return
        }
        val zoom = fitRadiusMeters?.let { zoomToFit(target.latitude, it) } ?: FOCUSED_ZOOM
        current.animateCamera(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), zoom))
    }

    internal fun attach(map: MapLibreMap) {
        this.map = map
        pendingTarget?.let { moveTo(it, pendingRadius) }
        pendingTarget = null
        pendingRadius = null
    }

    internal fun detach() {
        map = null
    }
}

/**
 * MapLibre MapView wrapped for Compose. Draws the geofence as a filled circle with a centre dot
 * and reports taps. MapView needs the host lifecycle forwarded to it, like a classic View.
 */
@Composable
fun GeofenceMap(
    center: Coordinates?,
    radiusMeters: Float,
    onMapClick: (Coordinates) -> Unit,
    controller: GeofenceMapController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    var loadedStyle by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder().target(DEFAULT_CENTER).zoom(DEFAULT_ZOOM).build()
                map.addOnMapClickListener { point ->
                    currentOnMapClick(Coordinates(point.latitude, point.longitude))
                    true
                }
                map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                    addGeofenceLayers(style, accentColor)
                    loadedStyle = style
                }
                controller.attach(map)
            }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        // addObserver replays ON_START / ON_RESUME if the screen is already in those states.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.detach()
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(loadedStyle, center, radiusMeters) {
        loadedStyle?.let { showGeofence(it, center, radiusMeters) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addGeofenceLayers(style: Style, color: Int) {
    style.addSource(GeoJsonSource(GEOFENCE_SOURCE))
    // One source holds the circle polygon and the centre point.
    style.addLayer(
        FillLayer("geofence-fill", GEOFENCE_SOURCE)
            .withProperties(PropertyFactory.fillColor(color), PropertyFactory.fillOpacity(0.2f))
    )
    style.addLayer(
        LineLayer("geofence-outline", GEOFENCE_SOURCE)
            .withProperties(PropertyFactory.lineColor(color), PropertyFactory.lineWidth(2f))
    )
    style.addLayer(
        CircleLayer("geofence-center", GEOFENCE_SOURCE)
            .withProperties(
                PropertyFactory.circleColor(color),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(2f),
            )
            // A circle layer would also put a dot on every vertex of the polygon: points only.
            .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Point")))
    )
}

private fun showGeofence(style: Style, center: Coordinates?, radiusMeters: Float) {
    val source = style.getSourceAs<GeoJsonSource>(GEOFENCE_SOURCE) ?: return
    if (center == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val point = Point.fromLngLat(center.longitude, center.latitude)
    val ring = GeoMath.circle(center, radiusMeters.toDouble(), CIRCLE_STEPS)
        .map { Point.fromLngLat(it.longitude, it.latitude) }
    val circle = Polygon.fromLngLats(listOf(ring))
    source.setGeoJson(
        FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(circle), Feature.fromGeometry(point)))
    )
}

private fun Coordinates.toLatLng() = LatLng(latitude, longitude)

/**
 * Zoom level at which a circle of [radiusMeters] is about [ZONE_DIAMETER_DP] wide.
 * At zoom z one 512-dp map tile covers the Earth's circumference * cos(latitude) / 2^z metres.
 */
private fun zoomToFit(latitude: Double, radiusMeters: Float): Double {
    val metersAtZoomZero = EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(latitude)) / TILE_SIZE_DP
    val metersPerDpNeeded = 2 * radiusMeters / ZONE_DIAMETER_DP
    return log2(metersAtZoomZero / metersPerDpNeeded).coerceIn(MIN_FIT_ZOOM, MAX_FIT_ZOOM)
}

private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.7
private const val TILE_SIZE_DP = 512.0
private const val ZONE_DIAMETER_DP = 180.0
private const val MIN_FIT_ZOOM = 3.0
private const val MAX_FIT_ZOOM = 19.5
