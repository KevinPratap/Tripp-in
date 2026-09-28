package com.trippin.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trippin.R
import com.trippin.core.design.TrippinTheme
import com.trippin.core.network.GeoPointDto
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** A numbered stop of the plan. */
data class MapStop(val point: GeoPointDto, val label: String)

/** One walk between stops. A straight leg is drawn dashed, because it does not follow the streets. */
data class MapLeg(val points: List<GeoPointDto>, val straight: Boolean)

/** A place to show as a coloured dot, tappable. */
data class MapPlace(val id: String, val point: GeoPointDto, val color: Color)

/**
 * OpenStreetMap vector tiles from OpenFreeMap: free, no key, no usage billing, which keeps the app on
 * its zero-cost stack. The map data is OpenStreetMap's, credited by the map's own attribution button.
 */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

private const val SRC_LEGS = "trippin-legs"
private const val SRC_PLACES = "trippin-places"
private const val SRC_STOPS = "trippin-stops"
private const val SRC_ME = "trippin-me"
private const val LAYER_PLACES = "trippin-places-layer"

/**
 * The app's real map: the day's walks along the streets, numbered stops, coloured places to tap, and
 * where the traveller is. Everything drawn here comes from the caller; this only renders it.
 *
 * [fitKey] decides when the camera reframes: it moves to show everything whenever the key changes
 * (a new day, a new search), not on every small update such as selecting a place.
 */
@Composable
fun TrippinMap(
    modifier: Modifier = Modifier,
    stops: List<MapStop> = emptyList(),
    legs: List<MapLeg> = emptyList(),
    places: List<MapPlace> = emptyList(),
    selectedPlaceId: String? = null,
    me: GeoPointDto? = null,
    focus: GeoPointDto? = null,
    fitKey: Any? = null,
    onPlaceTap: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val colors = TrippinTheme.colors
    val accent = colors.accent.toArgb()
    val ink = colors.ink.toArgb()
    val onAccent = colors.onAccent.toArgb()
    val tapHandler by rememberUpdatedState(onPlaceTap)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
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
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.addOnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                val slop = 18 * density
                val hit = m.queryRenderedFeatures(
                    RectF(screen.x - slop, screen.y - slop, screen.x + slop, screen.y + slop),
                    LAYER_PLACES
                ).firstOrNull()
                tapHandler(hit?.getStringProperty("id"))
                true
            }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                installLayers(loaded, accent, ink)
                style = loaded
            }
        }
        mapView
    }, modifier = modifier)

    val s = style
    LaunchedEffect(s, stops, legs, places, selectedPlaceId, me) {
        if (s == null || !s.isFullyLoaded) return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_LEGS)?.setGeoJson(
            FeatureCollection.fromFeatures(legs.filter { it.points.size >= 2 }.map { leg ->
                Feature.fromGeometry(LineString.fromLngLats(leg.points.map { Point.fromLngLat(it.longitude, it.latitude) }))
                    .apply { addBooleanProperty("straight", leg.straight) }
            })
        )
        s.getSourceAs<GeoJsonSource>(SRC_PLACES)?.setGeoJson(
            FeatureCollection.fromFeatures(places.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.point.longitude, p.point.latitude)).apply {
                    addStringProperty("id", p.id)
                    addStringProperty("color", hex(p.color.toArgb()))
                    addBooleanProperty("selected", p.id == selectedPlaceId)
                }
            })
        )
        stops.forEach { stop ->
            val name = "stop-${stop.label}"
            if (s.getImage(name) == null) s.addImage(name, stopBadge(context, stop.label, density, accent, ink, onAccent))
        }
        s.getSourceAs<GeoJsonSource>(SRC_STOPS)?.setGeoJson(
            FeatureCollection.fromFeatures(stops.map { stop ->
                Feature.fromGeometry(Point.fromLngLat(stop.point.longitude, stop.point.latitude))
                    .apply { addStringProperty("icon", "stop-${stop.label}") }
            })
        )
        s.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(me?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }))
        )
    }

    LaunchedEffect(s, fitKey) {
        val m = map ?: return@LaunchedEffect
        if (s == null) return@LaunchedEffect
        val points = (legs.flatMap { it.points } + stops.map { it.point } + places.map { it.point } + listOfNotNull(me))
            .map { LatLng(it.latitude, it.longitude) }
            .distinct()
        when {
            points.size >= 2 -> m.animateCamera(
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), (56 * density).toInt()),
                700
            )
            points.size == 1 -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 15.5), 700)
        }
    }

    LaunchedEffect(s, focus) {
        val m = map ?: return@LaunchedEffect
        val f = focus ?: return@LaunchedEffect
        if (s == null) return@LaunchedEffect
        val zoom = maxOf(m.cameraPosition.zoom, 15.5)
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.latitude, f.longitude), zoom), 500)
    }
}

private fun installLayers(style: Style, accent: Int, ink: Int) {
    style.addSource(GeoJsonSource(SRC_LEGS, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(SRC_PLACES, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(SRC_STOPS, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(SRC_ME, FeatureCollection.fromFeatures(emptyList())))

    // A pale casing under the walk so it reads on any street colour.
    style.addLayer(
        LineLayer("trippin-legs-casing", SRC_LEGS).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.9f)
        )
    )
    style.addLayer(
        LineLayer("trippin-legs-street", SRC_LEGS)
            .withFilter(Expression.eq(Expression.get("straight"), false))
            .withProperties(
                PropertyFactory.lineColor(accent),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
    )
    style.addLayer(
        LineLayer("trippin-legs-straight", SRC_LEGS)
            .withFilter(Expression.eq(Expression.get("straight"), true))
            .withProperties(
                PropertyFactory.lineColor(ink),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
    )
    style.addLayer(
        CircleLayer(LAYER_PLACES, SRC_PLACES).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(
                Expression.switchCase(Expression.eq(Expression.get("selected"), true), Expression.literal(11f), Expression.literal(6.5f))
            ),
            PropertyFactory.circleStrokeColor(
                Expression.switchCase(Expression.eq(Expression.get("selected"), true), Expression.color(ink), Expression.color(android.graphics.Color.WHITE))
            ),
            PropertyFactory.circleStrokeWidth(
                Expression.switchCase(Expression.eq(Expression.get("selected"), true), Expression.literal(3f), Expression.literal(2f))
            )
        )
    )
    style.addLayer(
        CircleLayer("trippin-me-halo", SRC_ME).withProperties(
            PropertyFactory.circleColor("#2563EB"),
            PropertyFactory.circleOpacity(0.18f),
            PropertyFactory.circleRadius(20f)
        )
    )
    style.addLayer(
        CircleLayer("trippin-me-dot", SRC_ME).withProperties(
            PropertyFactory.circleColor("#2563EB"),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
    style.addLayer(
        SymbolLayer("trippin-stops-layer", SRC_STOPS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    )
}

/** A stop's pin: the brand crimson disc, an ink ring and its number in the app's own typeface. */
private fun stopBadge(context: Context, label: String, density: Float, accent: Int, ink: Int, onAccent: Int): Bitmap {
    val size = (30 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(c, c, c, paint)
    paint.color = accent
    canvas.drawCircle(c, c, c - 2 * density, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.75f * density
    paint.color = ink
    canvas.drawCircle(c, c, c - 2.5f * density, paint)
    paint.style = Paint.Style.FILL
    paint.color = onAccent
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 13 * density
    paint.typeface = runCatching { ResourcesCompat.getFont(context, R.font.geist_bold) }.getOrNull()
    val baseline = c - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(label, c, baseline, paint)
    return bitmap
}

private fun hex(argb: Int): String = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and argb)
