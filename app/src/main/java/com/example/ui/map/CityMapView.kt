package com.example.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.CityRoadNetwork
import com.example.model.LatLng
import com.example.model.Vehicle
import com.example.model.VehicleStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CityBorderNavy
import com.example.ui.theme.CityCardNavy
import com.example.ui.theme.CityDarkNavy
import com.example.ui.theme.CitySurfaceNavy
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * MapLibre-backed operations map.
 *
 * Vehicles and routes arrive as server snapshots; markers are interpolated on every animation
 * frame but GeoJSON is pushed at ~12 Hz, so rendering stays under the 15 Hz update budget.
 */
@Composable
fun CityMapView(
    vehicles: List<Vehicle>,
    roadNetwork: CityRoadNetwork,
    selectedVehicleId: String?,
    onVehicleSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    val animated = remember { mutableStateMapOf<String, MapLatLng>() }
    val animatedProgress = remember { mutableStateMapOf<String, Float>() }
    val vehiclesState = rememberUpdatedState(vehicles)
    val selectedState = rememberUpdatedState(selectedVehicleId)
    val onSelectState = rememberUpdatedState(onVehicleSelect)

    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView
        if (view != null) {
            when (lifecycleOwner.lifecycle.currentState) {
                Lifecycle.State.RESUMED -> { view.onStart(); view.onResume() }
                Lifecycle.State.STARTED -> view.onStart()
                else -> Unit
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view?.onStart()
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_STOP -> view?.onStop()
                Lifecycle.Event.ON_DESTROY -> view?.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.onPause()
            view?.onStop()
            view?.onDestroy()
        }
    }

    // Initialise GeoJSON sources/layers once the style is ready.
    LaunchedEffect(styleReady, style) {
        val currentStyle = style ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        if (currentStyle.getSource("vehicles-source") == null) {
            currentStyle.addSource(GeoJsonSource("zones-source", FeatureCollection.fromFeatures(
                roadNetwork.zones.map { zone ->
                    Feature.fromGeometry(
                        Polygon.fromLngLats(listOf(listOf(
                            Point.fromLngLat(zone.bounds.minLng, zone.bounds.minLat),
                            Point.fromLngLat(zone.bounds.maxLng, zone.bounds.minLat),
                            Point.fromLngLat(zone.bounds.maxLng, zone.bounds.maxLat),
                            Point.fromLngLat(zone.bounds.minLng, zone.bounds.maxLat),
                            Point.fromLngLat(zone.bounds.minLng, zone.bounds.minLat),
                        )))
                    )
                }
            )))
            currentStyle.addSource(GeoJsonSource("pois-source", FeatureCollection.fromFeatures(
                roadNetwork.pois.map { poi ->
                    Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
                        addStringProperty("poiType", poi.poiType)
                    }
                }
            )))
            currentStyle.addSource(GeoJsonSource("vehicles-source", FeatureCollection.fromFeatures(emptyList())))
            currentStyle.addSource(GeoJsonSource("selected-source", FeatureCollection.fromFeatures(emptyList())))
            currentStyle.addSource(GeoJsonSource("route-source", FeatureCollection.fromFeatures(emptyList())))
            currentStyle.addSource(GeoJsonSource("pickup-source", FeatureCollection.fromFeatures(emptyList())))
            currentStyle.addSource(GeoJsonSource("dropoff-source", FeatureCollection.fromFeatures(emptyList())))

            currentStyle.addLayer(
                FillLayer("zones-fill-layer", "zones-source").withProperties(
                    PropertyFactory.fillColor("#78F2C2"),
                    PropertyFactory.fillOpacity(0.04f)
                )
            )
            currentStyle.addLayer(
                LineLayer("zones-outline-layer", "zones-source").withProperties(
                    PropertyFactory.lineColor("#78F2C2"),
                    PropertyFactory.lineOpacity(0.22f),
                    PropertyFactory.lineWidth(1.5f)
                )
            )
            currentStyle.addLayer(
                CircleLayer("pois-layer", "pois-source").withProperties(
                    PropertyFactory.circleRadius(4f),
                    PropertyFactory.circleColor("#FFB300"),
                    PropertyFactory.circleOpacity(0.8f),
                    PropertyFactory.circleStrokeWidth(1f),
                    PropertyFactory.circleStrokeColor("#071220")
                )
            )
            currentStyle.addLayer(
                LineLayer("route-glow-layer", "route-source").withProperties(
                    PropertyFactory.lineColor("#78F2C2"),
                    PropertyFactory.lineOpacity(0.35f),
                    PropertyFactory.lineWidth(12f)
                )
            )
            currentStyle.addLayer(
                LineLayer("route-line-layer", "route-source").withProperties(
                    PropertyFactory.lineColor("#78F2C2"),
                    PropertyFactory.lineOpacity(0.95f),
                    PropertyFactory.lineWidth(5f)
                )
            )
            currentStyle.addLayer(
                CircleLayer("pickup-layer", "pickup-source").withProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor("#FFB300"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#071220")
                )
            )
            currentStyle.addLayer(
                CircleLayer("dropoff-layer", "dropoff-source").withProperties(
                    PropertyFactory.circleRadius(9f),
                    PropertyFactory.circleColor("#00E676"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#071220")
                )
            )
            currentStyle.addLayer(
                CircleLayer("vehicles-layer", "vehicles-source").withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor("#78F2C2"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#071220")
                )
            )
            currentStyle.addLayer(
                CircleLayer("selected-ring-layer", "selected-source").withProperties(
                    PropertyFactory.circleRadius(16f),
                    PropertyFactory.circleColor("#000000"),
                    PropertyFactory.circleOpacity(0f),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor("#78F2C2")
                )
            )
        }
    }

    // Frame interpolation: ease marker positions toward each new server snapshot.
    LaunchedEffect(styleReady, vehicles) {
        if (!styleReady) return@LaunchedEffect
        var lastPush = 0L
        while (isActive) {
            withFrameNanos { }
            val current = vehiclesState.value
            for (v in current) {
                if (v.currentRoute.size >= 2) {
                    // Follow the routed polyline by distance; never interpolate across a corner.
                    val targetProgress = v.routeProgress.coerceIn(0f, 1f)
                    val previous = animatedProgress[v.id] ?: targetProgress
                    val next = if (targetProgress + 0.05f < previous) targetProgress
                        else previous + (targetProgress - previous) * 0.14f
                    animatedProgress[v.id] = next
                    animated[v.id] = positionOnRoute(v.currentRoute, next)
                } else {
                    val target = MapLatLng(v.currentLocation.latitude, v.currentLocation.longitude)
                    val existing = animated[v.id]
                    animated[v.id] = if (existing == null) target else MapLatLng(
                        existing.latitude + (target.latitude - existing.latitude) * 0.16,
                        existing.longitude + (target.longitude - existing.longitude) * 0.16,
                    )
                }
            }
            // Drop markers for vehicles that were sold/removed.
            val ids = current.map { it.id }.toSet()
            animated.keys.filter { it !in ids }.forEach { animated.remove(it) }
            animatedProgress.keys.filter { it !in ids }.forEach { animatedProgress.remove(it) }

            val now = System.nanoTime()
            if (now - lastPush > 80_000_000L) {
                lastPush = now
                pushVehicleFeatures(style, current, animated, selectedState.value)
                selectedState.value?.let { id ->
                    animated[id]?.let { point -> pushSelectedFeature(style, point) }
                }
            }
            delay(16)
        }
    }

    // Route geometry and pickup/dropoff endpoints for the selected vehicle.
    LaunchedEffect(styleReady, selectedVehicleId, vehicles) {
        val currentStyle = style ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val v = vehicles.find { it.id == selectedVehicleId }
        val route = v?.currentRoute.orEmpty()
        val line = (currentStyle.getSourceAs<GeoJsonSource>("route-source"))
        val pickup = currentStyle.getSourceAs<GeoJsonSource>("pickup-source")
        val dropoff = currentStyle.getSourceAs<GeoJsonSource>("dropoff-source")
        if (route.size >= 2) {
            line?.setGeoJson(LineString.fromLngLats(route.map { Point.fromLngLat(it.longitude, it.latitude) }))
            pickup?.setGeoJson(pointFeature(route.first(), "endpoint"))
            dropoff?.setGeoJson(pointFeature(route.last(), "endpoint"))
        } else {
            line?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            pickup?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            dropoff?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    // Only recenter when the operator explicitly selects a different vehicle.
    LaunchedEffect(selectedVehicleId) {
        val v = selectedVehicleId?.let { id -> vehicles.find { it.id == id } } ?: return@LaunchedEffect
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(MapLatLng(v.currentLocation.latitude, v.currentLocation.longitude), 14.2)
        )
    }

    Box(modifier = modifier.fillMaxSize().background(CityDarkNavy)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("city_map_canvas"),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    onCreate(null)
                    getMapAsync { mapInstance ->
                        map = mapInstance
                        mapInstance.cameraPosition = CameraPosition.Builder()
                            .target(MapLatLng(-33.9220, 18.4350))
                            .zoom(13.6)
                            .build()
                        mapInstance.addOnMapClickListener { latLng ->
                            val screenPoint = mapInstance.projection.toScreenLocation(latLng)
                            val feature = mapInstance.queryRenderedFeatures(screenPoint, "vehicles-layer")
                                .firstOrNull { it.getStringProperty("vehicleId") != null }
                            val id = feature?.getStringProperty("vehicleId")
                            if (id != null) {
                                onSelectState.value(id)
                                true
                            } else false
                        }
                        mapInstance.setStyle(Style.Builder().fromUri("asset://style_osm_dark.json")) { loaded ->
                            style = loaded
                            styleReady = true
                        }
                    }
                    mapView = this
                }
            },
            update = { /* state is pushed through the effects above */ },
        )

        // Map controls
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CitySurfaceNavy.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, CityBorderNavy)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    IconButton(
                        onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) },
                        modifier = Modifier.size(40.dp).testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = TextPrimaryDark)
                    }
                    IconButton(
                        onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) },
                        modifier = Modifier.size(40.dp).testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextPrimaryDark)
                    }
                    IconButton(
                        onClick = {
                            map?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(MapLatLng(-33.9220, 18.4350), 13.6)
                            )
                        },
                        modifier = Modifier.size(40.dp).testTag("center_map_btn")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Reset Center", tint = CyanNeon)
                    }
                }
            }
        }

        // Required visible OSM attribution (also present in the MapLibre attribution dialog).
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.75f)
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                color = TextMutedDark,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun positionOnRoute(route: List<LatLng>, progress: Float): MapLatLng {
    if (route.isEmpty()) return MapLatLng(0.0, 0.0)
    if (route.size == 1 || progress <= 0f) return MapLatLng(route.first().latitude, route.first().longitude)
    if (progress >= 1f) return MapLatLng(route.last().latitude, route.last().longitude)
    val segments = route.zipWithNext { a, b -> a.distanceTo(b) }
    val total = segments.sum()
    if (total <= 0.0) return MapLatLng(route.first().latitude, route.first().longitude)
    var remaining = total * progress
    for (i in segments.indices) {
        val length = segments[i]
        if (remaining <= length && length > 0.0) {
            val f = (remaining / length).toFloat()
            val a = route[i]
            val b = route[i + 1]
            return MapLatLng(
                a.latitude + (b.latitude - a.latitude) * f,
                a.longitude + (b.longitude - a.longitude) * f,
            )
        }
        remaining -= length
    }
    return MapLatLng(route.last().latitude, route.last().longitude)
}

private fun pointFeature(latLng: LatLng, id: String): Feature =
    Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude)).apply {
        addStringProperty("kind", id)
    }

private fun pushVehicleFeatures(
    style: Style?,
    vehicles: List<Vehicle>,
    animated: Map<String, MapLatLng>,
    selectedId: String?,
) {
    val source = style?.getSourceAs<GeoJsonSource>("vehicles-source") ?: return
    val features = vehicles.mapNotNull { v ->
        val point = animated[v.id] ?: return@mapNotNull null
        Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
            addStringProperty("vehicleId", v.id)
            addStringProperty("status", v.status.name)
            addBooleanProperty("selected", v.id == selectedId)
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun pushSelectedFeature(style: Style?, point: MapLatLng) {
    val source = style?.getSourceAs<GeoJsonSource>("selected-source") ?: return
    source.setGeoJson(
        FeatureCollection.fromFeatures(
            listOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)))
        )
    )
}
