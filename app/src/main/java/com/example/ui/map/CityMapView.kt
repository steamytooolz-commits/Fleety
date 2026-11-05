package com.example.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CityRoadNetwork
import com.example.model.CityZone
import com.example.model.LatLng
import com.example.model.LocationPoint
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
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow

@Composable
fun CityMapView(
    vehicles: List<Vehicle>,
    roadNetwork: CityRoadNetwork,
    selectedVehicleId: String?,
    onVehicleSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Map viewport state: center lat/lng & zoom
    var centerLat by remember { mutableStateOf(-33.9220) }
    var centerLng by remember { mutableStateOf(18.4350) }
    var zoomLevel by remember { mutableFloatStateOf(13.8f) }

    // Layer toggles
    var showPois by remember { mutableStateOf(true) }
    var showZones by remember { mutableStateOf(true) }
    var showTrafficHeat by remember { mutableStateOf(false) }

    // Selected vehicle focus
    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(CityDarkNavy)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(100f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(100f)

        // Web Mercator city projection scale:
        // Circumference of earth at zoom z = 256 * 2^z px
        // 360 deg longitude = 256 * 2^z px
        // pxPerDegLng = (256 * 2^z) / 360
        // At zoom 13.8: ~10,100 px per degree.
        val pxPerDegLng = (256f * 2f.pow(zoomLevel) / 360f)
        val cosCenterLat = cos(Math.toRadians(centerLat)).toFloat().coerceIn(0.5f, 1.0f)
        val pxPerDegLat = pxPerDegLng / cosCenterLat

        fun project(latLng: LatLng): Offset {
            val x = (latLng.longitude - centerLng).toFloat() * pxPerDegLng + (widthPx / 2f)
            val y = (centerLat - latLng.latitude).toFloat() * pxPerDegLat + (heightPx / 2f)
            return Offset(x, y)
        }

        fun unproject(offset: Offset): LatLng {
            val lng = (offset.x - widthPx / 2f) / pxPerDegLng + centerLng
            val lat = centerLat - (offset.y - heightPx / 2f) / pxPerDegLat
            return LatLng(lat, lng)
        }

        // Auto-center on selected vehicle if clicked
        LaunchedEffect(selectedVehicleId) {
            selectedVehicle?.let { v ->
                centerLat = v.currentLocation.latitude
                centerLng = v.currentLocation.longitude
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("city_map_canvas")
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) {
                            val zoomDelta = log2(zoom.toDouble()).toFloat()
                            zoomLevel = (zoomLevel + zoomDelta).coerceIn(11.5f, 16.5f)
                        }
                        val currentPxPerLng = (256f * 2f.pow(zoomLevel) / 360f)
                        val currentPxPerLat = currentPxPerLng / cosCenterLat
                        centerLng -= pan.x / currentPxPerLng
                        centerLat += pan.y / currentPxPerLat
                    }
                }
                .pointerInput(vehicles) {
                    detectTapGestures { tapOffset ->
                        val tappedVehicle = vehicles.minByOrNull { v ->
                            val pt = project(v.currentLocation)
                            (pt - tapOffset).getDistance()
                        }
                        if (tappedVehicle != null) {
                            val pt = project(tappedVehicle.currentLocation)
                            if ((pt - tapOffset).getDistance() < 60f) {
                                onVehicleSelect(tappedVehicle.id)
                            }
                        }
                    }
                }
        ) {
            // 1. Draw Waterways & Coastline
            drawWaterAndCoastline(this, ::project, widthPx, heightPx)

            // 2. Draw Zones if enabled
            if (showZones) {
                drawZoneBoundaries(this, roadNetwork.zones, ::project, textMeasurer, widthPx, heightPx)
            }

            // 3. Draw Road Network (OSM Highway and Street network)
            drawRoadNetwork(this, roadNetwork, ::project, showTrafficHeat, widthPx, heightPx)

            // 4. Draw Selected Vehicle Route Polyline
            if (selectedVehicle != null && selectedVehicle.currentRoute.size >= 2) {
                drawActiveRoutePolyline(this, selectedVehicle, ::project)
            }

            // 5. Draw POIs
            if (showPois) {
                drawPois(this, roadNetwork.pois, ::project, textMeasurer, widthPx, heightPx)
            }

            // 6. Viewport-culled Vehicle Markers with 60 FPS interpolation
            drawVehicleMarkers(
                scope = this,
                vehicles = vehicles,
                project = ::project,
                widthPx = widthPx,
                heightPx = heightPx,
                selectedId = selectedVehicleId,
                textMeasurer = textMeasurer
            )
        }

        // Top Layer Controls: Zoom controls & Layer toggle
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CitySurfaceNavy.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel + 0.5f).coerceAtMost(16.5f) },
                        modifier = Modifier.size(40.dp).testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = TextPrimaryDark)
                    }
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel - 0.5f).coerceAtLeast(11.5f) },
                        modifier = Modifier.size(40.dp).testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextPrimaryDark)
                    }
                    IconButton(
                        onClick = {
                            centerLat = -33.9220
                            centerLng = 18.4350
                            zoomLevel = 13.8f
                        },
                        modifier = Modifier.size(40.dp).testTag("center_map_btn")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Reset Center", tint = CyanNeon)
                    }
                    IconButton(
                        onClick = { showTrafficHeat = !showTrafficHeat },
                        modifier = Modifier.size(40.dp).testTag("toggle_traffic_btn")
                    ) {
                        Icon(
                            if (showTrafficHeat) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Traffic Index",
                            tint = if (showTrafficHeat) AmberWarning else TextSecondaryDark
                        )
                    }
                }
            }
        }

        // Selected Vehicle HUD Card
        if (selectedVehicle != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 48.dp, end = 16.dp)
                    .fillMaxWidth(0.92f)
                    .testTag("vehicle_hud_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy.copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                when (selectedVehicle.status) {
                                    VehicleStatus.BREAKDOWN -> CrimsonError.copy(alpha = 0.2f)
                                    VehicleStatus.IDLE -> CyanNeon.copy(alpha = 0.2f)
                                    else -> EmeraldSuccess.copy(alpha = 0.2f)
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = when (selectedVehicle.status) {
                                VehicleStatus.BREAKDOWN -> CrimsonError
                                VehicleStatus.IDLE -> CyanNeon
                                else -> EmeraldSuccess
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedVehicle.plate,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimaryDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = when (selectedVehicle.status) {
                                    VehicleStatus.BREAKDOWN -> CrimsonError
                                    VehicleStatus.IDLE -> CityCardNavy
                                    VehicleStatus.FORCED_REST -> AmberWarning
                                    else -> EmeraldSuccess
                                }
                            ) {
                                Text(
                                    text = selectedVehicle.status.name.replace("_", " "),
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            }
                        }
                        Text(
                            text = "${selectedVehicle.vehicleClass.label} • Fuel: ${selectedVehicle.fuel.toInt()}% • Condition: ${selectedVehicle.condition.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }

        // OpenStreetMap Attribution banner
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.75f)
        ) {
            Text(
                text = "Map data: © OpenStreetMap contributors",
                color = TextMutedDark,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// --- Canvas Drawing Subroutines ---

private fun drawWaterAndCoastline(
    scope: DrawScope,
    project: (LatLng) -> Offset,
    w: Float,
    h: Float
) {
    val p1 = project(LatLng(-33.8900, 18.4000))
    val p2 = project(LatLng(-33.8950, 18.4350))
    val p3 = project(LatLng(-33.9100, 18.4850))

    val waterPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(p3.x.coerceIn(-500f, w + 500f), p3.y.coerceIn(-500f, h + 500f))
        lineTo(p2.x.coerceIn(-500f, w + 500f), p2.y.coerceIn(-500f, h + 500f))
        lineTo(p1.x.coerceIn(-500f, w + 500f), p1.y.coerceIn(-500f, h + 500f))
        close()
    }
    scope.drawPath(waterPath, color = Color(0xFF071220))
}

private fun drawZoneBoundaries(
    scope: DrawScope,
    zones: List<CityZone>,
    project: (LatLng) -> Offset,
    textMeasurer: TextMeasurer,
    widthPx: Float,
    heightPx: Float
) {
    for (zone in zones) {
        val topLeft = project(LatLng(zone.bounds.maxLat, zone.bounds.minLng))
        val bottomRight = project(LatLng(zone.bounds.minLat, zone.bounds.maxLng))

        // Viewport culling: skip if completely outside visible screen
        if (bottomRight.x < -100f || topLeft.x > widthPx + 100f ||
            bottomRight.y < -100f || topLeft.y > heightPx + 100f
        ) {
            continue
        }

        val rectWidth = (bottomRight.x - topLeft.x).coerceIn(10f, widthPx * 3f)
        val rectHeight = (bottomRight.y - topLeft.y).coerceIn(10f, heightPx * 3f)

        val fillColor = if (zone.unlocked) CyanNeon.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.02f)
        val strokeColor = if (zone.unlocked) CyanNeon.copy(alpha = 0.22f) else CityBorderNavy.copy(alpha = 0.4f)

        scope.drawRect(
            color = fillColor,
            topLeft = topLeft,
            size = Size(rectWidth, rectHeight)
        )

        scope.drawRect(
            color = strokeColor,
            topLeft = topLeft,
            size = Size(rectWidth, rectHeight),
            style = Stroke(
                width = 2f,
                pathEffect = if (!zone.unlocked) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
            )
        )

        // Draw label safely clamped to visible on-screen region
        val labelX = topLeft.x.coerceIn(16f, (widthPx - 200f).coerceAtLeast(16f))
        val labelY = topLeft.y.coerceIn(16f, (heightPx - 40f).coerceAtLeast(16f))

        val zoneLayout = textMeasurer.measure(
            text = "${zone.name} ${if (!zone.unlocked) "(Locked)" else ""}",
            style = TextStyle(
                color = if (zone.unlocked) CyanNeon.copy(alpha = 0.7f) else TextMutedDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
        scope.drawText(
            textLayoutResult = zoneLayout,
            topLeft = Offset(labelX, labelY)
        )
    }
}

private fun drawRoadNetwork(
    scope: DrawScope,
    roadNetwork: CityRoadNetwork,
    project: (LatLng) -> Offset,
    showTraffic: Boolean,
    widthPx: Float,
    heightPx: Float
) {
    val margin = 100f
    for ((fromId, edges) in roadNetwork.adjacency) {
        val fromNode = roadNetwork.nodes[fromId] ?: continue
        val startPt = project(fromNode.location)

        for (edge in edges) {
            val toNode = roadNetwork.nodes[edge.toNodeId] ?: continue
            val endPt = project(toNode.location)

            // Culling: check if road segment is within visible viewport margin
            val minX = kotlin.math.min(startPt.x, endPt.x)
            val maxX = kotlin.math.max(startPt.x, endPt.x)
            val minY = kotlin.math.min(startPt.y, endPt.y)
            val maxY = kotlin.math.max(startPt.y, endPt.y)

            if (maxX < -margin || minX > widthPx + margin || maxY < -margin || minY > heightPx + margin) {
                continue
            }

            val roadColor = if (showTraffic) {
                when {
                    edge.speedLimitKmh > 75.0 -> AmberWarning.copy(alpha = 0.8f)
                    else -> CyanNeon.copy(alpha = 0.5f)
                }
            } else {
                if (edge.speedLimitKmh >= 70.0) Color(0xFF2C3E5A) else Color(0xFF1E2B3E)
            }

            val strokeWidth = if (edge.speedLimitKmh >= 70.0) 6f else 3.5f

            scope.drawLine(
                color = roadColor,
                start = startPt,
                end = endPt,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun drawActiveRoutePolyline(
    scope: DrawScope,
    vehicle: Vehicle,
    project: (LatLng) -> Offset
) {
    val route = vehicle.currentRoute
    if (route.size < 2) return

    val path = Path()
    val firstPt = project(route[0])
    path.moveTo(firstPt.x, firstPt.y)

    for (i in 1 until route.size) {
        val pt = project(route[i])
        path.lineTo(pt.x, pt.y)
    }

    // Glow line
    scope.drawPath(
        path = path,
        color = CyanNeon.copy(alpha = 0.35f),
        style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Solid core line
    scope.drawPath(
        path = path,
        color = CyanNeon,
        style = Stroke(
            width = 4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
        )
    )

    // Pickup & Dropoff terminus markers
    val pickupPt = project(route.first())
    val dropoffPt = project(route.last())

    scope.drawCircle(color = AmberWarning, radius = 7f, center = pickupPt)
    scope.drawCircle(color = EmeraldSuccess, radius = 8f, center = dropoffPt)
}

private fun drawPois(
    scope: DrawScope,
    pois: List<LocationPoint>,
    project: (LatLng) -> Offset,
    textMeasurer: TextMeasurer,
    widthPx: Float,
    heightPx: Float
) {
    for (poi in pois) {
        val pt = project(poi.toLatLng())

        // Culling: only draw POIs on screen
        if (pt.x < -40f || pt.x > widthPx + 40f || pt.y < -40f || pt.y > heightPx + 40f) {
            continue
        }

        val poiColor = when (poi.poiType) {
            "RESTAURANT" -> AmberWarning
            "TRANSIT_HUB" -> CyanNeon
            "WAREHOUSE" -> Color(0xFFB388FF)
            "INDUSTRIAL" -> Color(0xFF80D8FF)
            "FUEL_STATION" -> Color(0xFFFF6E40)
            else -> TextSecondaryDark
        }

        scope.drawCircle(color = poiColor.copy(alpha = 0.25f), radius = 9f, center = pt)
        scope.drawCircle(color = poiColor, radius = 4.5f, center = pt)

        if (pt.x in 10f..(widthPx - 100f) && pt.y in 10f..(heightPx - 20f)) {
            val poiLayout = textMeasurer.measure(
                text = poi.label,
                style = TextStyle(color = TextSecondaryDark, fontSize = 9.sp)
            )
            scope.drawText(
                textLayoutResult = poiLayout,
                topLeft = Offset(pt.x + 8f, pt.y - 7f)
            )
        }
    }
}

private fun drawVehicleMarkers(
    scope: DrawScope,
    vehicles: List<Vehicle>,
    project: (LatLng) -> Offset,
    widthPx: Float,
    heightPx: Float,
    selectedId: String?,
    textMeasurer: TextMeasurer
) {
    val buffer = 50f

    for (v in vehicles) {
        val pt = project(v.currentLocation)

        // Viewport culling
        if (pt.x < -buffer || pt.x > widthPx + buffer || pt.y < -buffer || pt.y > heightPx + buffer) {
            continue
        }

        val isSelected = v.id == selectedId
        val statusColor = when (v.status) {
            VehicleStatus.BREAKDOWN -> CrimsonError
            VehicleStatus.IDLE -> CyanNeon
            VehicleStatus.FORCED_REST -> AmberWarning
            VehicleStatus.DIVERT_TO_REFUEL -> Color(0xFFFF9100)
            else -> EmeraldSuccess
        }

        val baseRadius = if (isSelected) 18f else 13f

        // Halo ring
        scope.drawCircle(
            color = statusColor.copy(alpha = if (isSelected) 0.5f else 0.22f),
            radius = baseRadius + 8f,
            center = pt
        )

        // Core marker
        scope.drawCircle(
            color = CitySurfaceNavy,
            radius = baseRadius,
            center = pt
        )
        scope.drawCircle(
            color = statusColor,
            radius = baseRadius,
            center = pt,
            style = Stroke(width = if (isSelected) 3.5f else 2.5f)
        )

        // Directional arrow indicating heading
        scope.rotate(degrees = v.heading, pivot = pt) {
            val arrowPath = Path().apply {
                moveTo(pt.x, pt.y - baseRadius + 3f)
                lineTo(pt.x - 5f, pt.y + 4f)
                lineTo(pt.x, pt.y + 1f)
                lineTo(pt.x + 5f, pt.y + 4f)
                close()
            }
            scope.drawPath(path = arrowPath, color = statusColor)
        }

        // Plate tag
        if (pt.x in 25f..(widthPx - 25f) && pt.y in 15f..(heightPx - 30f)) {
            val plateLayout = textMeasurer.measure(
                text = v.plate.takeLast(7),
                style = TextStyle(
                    color = if (isSelected) CyanNeon else TextPrimaryDark,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            )
            scope.drawText(
                textLayoutResult = plateLayout,
                topLeft = Offset(pt.x - 22f, pt.y + baseRadius + 3f)
            )
        }
    }
}
