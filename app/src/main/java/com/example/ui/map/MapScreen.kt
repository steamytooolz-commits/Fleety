package com.example.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CityRoadNetwork
import com.example.data.ConnectionState
import com.example.model.Contract
import com.example.model.Staff
import com.example.model.Vehicle
import com.example.model.VehicleStatus
import com.example.model.shortLabel
import com.example.ui.components.CityCard
import com.example.ui.components.Dot
import com.example.ui.components.EmptyState
import com.example.ui.components.IconTile
import com.example.ui.components.MeterRow
import com.example.ui.components.StatusChip
import com.example.ui.components.km
import com.example.ui.components.meterFraction
import com.example.ui.components.simClock
import com.example.ui.theme.Amber
import com.example.ui.theme.Coral
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.Iris
import com.example.ui.theme.Mint
import com.example.ui.theme.Muted
import com.example.ui.theme.NumericStyle
import com.example.ui.theme.Paper
import com.example.ui.theme.PillShape
import com.example.ui.theme.Slate
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceHigh
import com.example.ui.theme.SurfaceRaised

/**
 * Live operations map. The map itself stays MapLibre-native; this wrapper owns the operator
 * overlays (clock, filters, fleet strip and the selected-asset card).
 */
@Composable
fun MapScreen(
    vehicles: List<Vehicle>,
    roadNetwork: CityRoadNetwork,
    staff: Map<String, Staff>,
    contracts: List<Contract>,
    selectedVehicleId: String?,
    currentTick: Long,
    isPaused: Boolean,
    connection: ConnectionState,
    onVehicleSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenFleet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Working", "Idle", "Attention")
    val visible = when (filterIndex) {
        0 -> vehicles
        1 -> vehicles.filter { it.isWorking }
        2 -> vehicles.filter { it.status == VehicleStatus.IDLE }
        else -> vehicles.filter { it.fuel < 20.0 || it.condition < 35.0 || it.isBroken }
    }
    val selected = vehicles.find { it.id == selectedVehicleId }
    val clock = simClock(currentTick)

    Box(modifier = modifier.fillMaxSize().testTag("map_screen_root")) {
        CityMapView(
            vehicles = visible,
            roadNetwork = roadNetwork,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelect = onVehicleSelect,
        )

        // Top overlay: clock, link state, pause control and filters.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = PillShape,
                    color = SurfaceBase.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(
                            color = when (connection) {
                                ConnectionState.ONLINE -> if (isPaused) Amber else Mint
                                ConnectionState.CONNECTING -> Amber
                                ConnectionState.OFFLINE -> Coral
                            },
                            size = 7,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(clock.label, style = NumericStyle, color = Paper)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isPaused) "HOLD" else "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPaused) Amber else Mint,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = SurfaceBase.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onTogglePause() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) Amber else Mint,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                filters.forEachIndexed { index, label ->
                    val active = index == filterIndex
                    val count = when (index) {
                        0 -> vehicles.size
                        1 -> vehicles.count { it.isWorking }
                        2 -> vehicles.count { it.status == VehicleStatus.IDLE }
                        else -> vehicles.count { it.fuel < 20.0 || it.condition < 35.0 || it.isBroken }
                    }
                    Surface(
                        shape = PillShape,
                        color = if (active) Mint else SurfaceBase.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Mint else Hairline),
                        modifier = Modifier.clickable { filterIndex = index },
                    ) {
                        Text(
                            "$label $count",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Ink else Slate,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // Bottom overlay: selected asset or the fleet strip.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected != null) {
                SelectedAssetCard(
                    vehicle = selected,
                    driver = staff[selected.assignedDriverId],
                    contract = contracts.find { it.id == selected.currentContractId },
                    onClose = onClearSelection,
                    onOpenFleet = onOpenFleet,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            if (visible.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceBase.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = if (vehicles.isEmpty()) "No vehicles in the fleet yet." else "No assets match this filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visible.forEach { vehicle ->
                        val isSelected = vehicle.id == selectedVehicleId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Mint.copy(alpha = 0.16f) else SurfaceBase.copy(alpha = 0.94f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Mint.copy(alpha = 0.7f) else Hairline,
                            ),
                            modifier = Modifier
                                .clickable {
                                    if (isSelected) onClearSelection() else onVehicleSelect(vehicle.id)
                                }
                                .testTag("map_vehicle_chip_${vehicle.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Dot(color = statusColor(vehicle), size = 6)
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        vehicle.plate,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) Mint else Paper,
                                    )
                                    Text(
                                        vehicle.status.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Muted,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedAssetCard(
    vehicle: Vehicle,
    driver: Staff?,
    contract: Contract?,
    onClose: () -> Unit,
    onOpenFleet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CityCard(
        modifier = modifier.fillMaxWidth(),
        accent = statusColor(vehicle),
        containerColor = SurfaceBase.copy(alpha = 0.97f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.LocalShipping, statusColor(vehicle), size = 38)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vehicle.plate, style = MaterialTheme.typography.titleSmall, color = Paper)
                    Spacer(Modifier.width(6.dp))
                    StatusChip(vehicle.status.shortLabel, statusColor(vehicle))
                }
                Text(
                    "${vehicle.vehicleClass.label} · ${driver?.name ?: "No driver"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(SurfaceHigh)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = Slate, modifier = Modifier.size(16.dp))
            }
        }

        if (contract != null) {
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Route, contentDescription = null, tint = Iris, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${contract.pickup.label} → ${contract.dropoff.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(km(vehicle.remainingKm), style = MaterialTheme.typography.labelSmall, color = Iris)
            }
        }

        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MeterRow(
                label = "Fuel",
                fraction = meterFraction(vehicle.fuel),
                valueLabel = "${vehicle.fuel.toInt()}%",
                color = if (vehicle.fuel < 20) Coral else Amber,
                modifier = Modifier.weight(1f),
            )
            MeterRow(
                label = "Condition",
                fraction = meterFraction(vehicle.condition),
                valueLabel = "${vehicle.condition.toInt()}%",
                color = if (vehicle.condition < 35) Coral else Iris,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier
                .clip(PillShape)
                .clickable { onOpenFleet() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Open in fleet", style = MaterialTheme.typography.labelMedium, color = Mint)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.LocalShipping, contentDescription = null, tint = Mint, modifier = Modifier.size(13.dp))
        }
    }
}

private fun statusColor(vehicle: Vehicle): Color = when {
    vehicle.status == VehicleStatus.BREAKDOWN -> Coral
    vehicle.fuel < 20.0 || vehicle.condition < 35.0 -> Amber
    vehicle.status == VehicleStatus.IDLE -> Slate
    else -> Mint
}
