package com.example.ui.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Contract
import com.example.model.ContractStatus
import com.example.model.ContractType
import com.example.model.Staff
import com.example.model.Vehicle
import com.example.model.VehicleStatus
import com.example.model.shortLabel
import com.example.simulation.MatchingEngine
import com.example.ui.components.CityCard
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterPills
import com.example.ui.components.IconTile
import com.example.ui.components.KeyValueRow
import com.example.ui.components.MeterRow
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.components.deadlineLabel
import com.example.ui.components.isLate
import com.example.ui.components.km
import com.example.ui.components.money
import com.example.ui.components.meterFraction
import com.example.ui.components.scorePercent
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
import com.example.ui.theme.Sky
import com.example.ui.theme.Slate
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceHigh
import com.example.ui.theme.SurfaceRaised
import java.util.Locale

private enum class BoardFilter(val label: String) {
    OPEN("Open"),
    ACTIVE("Active"),
    CLOSED("Closed"),
}

/**
 * Contract market. Open work can be inspected and matched against idle vehicles with the same
 * deterministic scoring the tick service uses, so the operator can see *why* a van wins a job.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchScreen(
    contracts: List<Contract>,
    vehicles: List<Vehicle>,
    staff: Map<String, Staff>,
    currentTick: Long,
    autoDispatchEnabled: Boolean,
    onToggleAutoDispatch: () -> Unit,
    onManualAssign: (contractId: String, vehicleId: String) -> Unit,
    onSpawnContract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    var sortIndex by remember { mutableIntStateOf(0) }
    var inspecting by remember { mutableStateOf<Contract?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val open = contracts.filter { it.status == ContractStatus.OPEN }
    val active = contracts.filter { it.isActive }
    val closed = contracts.filter { it.status == ContractStatus.COMPLETED || it.status == ContractStatus.FAILED }
    val sortLabels = listOf("Deadline", "Reward", "Newest")
    val displayed = when (BoardFilter.entries[filterIndex]) {
        BoardFilter.OPEN -> when (sortIndex) {
            0 -> open.sortedBy { it.deadlineTick }
            1 -> open.sortedByDescending { it.reward }
            else -> open.sortedByDescending { it.createdAtTick }
        }
        BoardFilter.ACTIVE -> active.sortedBy { it.deadlineTick }
        BoardFilter.CLOSED -> closed.sortedByDescending { it.completedAtTick ?: it.deadlineTick }
    }
    val boardValue = open.sumOf { it.reward }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dispatch_screen_root"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Column {
                Text("CONTRACT MARKET", style = MaterialTheme.typography.labelSmall, color = Mint)
                Spacer(Modifier.height(2.dp))
                Text("Dispatch board", style = MaterialTheme.typography.headlineSmall, color = Paper)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${open.size} unassigned · ${money(boardValue)} of work on the board",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }

        item("controls") {
            CityCard(accent = if (autoDispatchEnabled) Mint else Amber) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(
                        icon = Icons.Rounded.AutoAwesome,
                        tint = if (autoDispatchEnabled) Mint else Amber,
                        size = 40,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-dispatch", style = MaterialTheme.typography.titleSmall, color = Paper)
                        Text(
                            if (autoDispatchEnabled) "Matching engine assigns jobs each cycle"
                            else "Jobs wait for a manual assignment",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TogglePill(
                        enabled = autoDispatchEnabled,
                        onClick = onToggleAutoDispatch,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = "Poll demand",
                        icon = Icons.Rounded.Add,
                        onClick = onSpawnContract,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Sort: ${sortLabels[sortIndex]}",
                        icon = Icons.Rounded.Tune,
                        onClick = { sortIndex = (sortIndex + 1) % sortLabels.size },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item("filters") {
            FilterPills(
                items = listOf(
                    "Open ${open.size}",
                    "Active ${active.size}",
                    "Closed ${closed.size}",
                ),
                selectedIndex = filterIndex,
                onSelect = { filterIndex = it },
            )
        }

        if (displayed.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = when (BoardFilter.entries[filterIndex]) {
                        BoardFilter.OPEN -> "No work on the board"
                        BoardFilter.ACTIVE -> "Nothing in transit"
                        BoardFilter.CLOSED -> "No completed jobs yet"
                    },
                    message = when (BoardFilter.entries[filterIndex]) {
                        BoardFilter.OPEN -> "New demand arrives every cycle. Poll the board or wait for the market."
                        BoardFilter.ACTIVE -> "Assign an open contract and it will appear here while the driver is on the road."
                        BoardFilter.CLOSED -> "Delivered and failed contracts are archived here for review."
                    },
                    icon = Icons.Rounded.Inventory2,
                    actionLabel = if (BoardFilter.entries[filterIndex] == BoardFilter.OPEN) "Poll demand" else null,
                    onAction = if (BoardFilter.entries[filterIndex] == BoardFilter.OPEN) onSpawnContract else null,
                )
            }
        }

        items(displayed, key = { it.id }) { contract ->
            ContractCard(
                contract = contract,
                vehicle = vehicles.find { it.id == contract.assignedVehicleId },
                currentTick = currentTick,
                onClick = { inspecting = contract },
            )
        }
    }

    val sheetContract = inspecting
    if (sheetContract != null) {
        ModalBottomSheet(
            onDismissRequest = { inspecting = null },
            sheetState = sheetState,
            containerColor = SurfaceBase,
            contentColor = Paper,
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(PillShape)
                        .background(Hairline)
                )
            },
        ) {
            ContractSheet(
                contract = sheetContract,
                vehicles = vehicles,
                staff = staff,
                currentTick = currentTick,
                onAssign = { vehicleId ->
                    onManualAssign(sheetContract.id, vehicleId)
                    inspecting = null
                },
            )
        }
    }
}

@Composable
private fun TogglePill(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(if (enabled) Mint else SurfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (enabled) Ink else Muted)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (enabled) "ON" else "OFF",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) Ink else Slate,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ContractCard(
    contract: Contract,
    vehicle: Vehicle?,
    currentTick: Long,
    onClick: () -> Unit,
) {
    val typeColor = Color(contract.type.baseColorHex)
    val late = isLate(contract.deadlineTick, currentTick) &&
        contract.status != ContractStatus.COMPLETED
    val statusColor = when (contract.status) {
        ContractStatus.OPEN -> Sky
        ContractStatus.ASSIGNED -> Iris
        ContractStatus.IN_PROGRESS -> Mint
        ContractStatus.COMPLETED -> Mint
        ContractStatus.FAILED -> Coral
    }

    CityCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = contractIcon(contract.type), tint = typeColor, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contract.type.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = Paper,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusChip(contract.status.shortLabel, statusColor)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${contract.pickup.label} → ${contract.dropoff.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(money(contract.reward), style = NumericStyle.copy(fontSize = 15.sp), color = Mint)
                Text(
                    deadlineLabel(contract.deadlineTick, currentTick),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (late) Coral else Muted,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MetaChip(Icons.Rounded.Route, km(contract.estimatedKm))
            MetaChip(Icons.Rounded.Scale, "${contract.payload.weightKg.toInt()} kg")
            if (contract.payload.requiresRefrigeration) {
                MetaChip(Icons.Rounded.AcUnit, "Chilled", color = Sky)
            }
            Spacer(Modifier.weight(1f))
            if (contract.isActive && vehicle != null) {
                StatusChip(vehicle.status.shortLabel, Mint)
            } else if (contract.status == ContractStatus.OPEN) {
                Text("Match →", style = MaterialTheme.typography.labelMedium, color = Mint)
            }
        }
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String, color: Color = Slate) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(SurfaceHigh.copy(alpha = 0.7f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, letterSpacing = 0.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractSheet(
    contract: Contract,
    vehicles: List<Vehicle>,
    staff: Map<String, Staff>,
    currentTick: Long,
    onAssign: (String) -> Unit,
) {
    val idle = vehicles.filter { it.status == VehicleStatus.IDLE }
    val ranked = idle
        .map { v -> v to MatchingEngine.scoreVehicleForContract(v, staff[v.assignedDriverId], contract) }
        .sortedByDescending { it.second.totalScore }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("sheet_title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(contractIcon(contract.type), Color(contract.type.baseColorHex), size = 44)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(contract.type.label, style = MaterialTheme.typography.titleLarge, color = Paper)
                    Text(
                        "${contract.pickup.zoneId.replaceFirstChar { it.uppercase() }} zone · ${contract.id.take(18)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                Text(money(contract.reward), style = MaterialTheme.typography.titleLarge, color = Mint)
            }
        }

        item("route") {
            CityCard(containerColor = SurfaceRaised) {
                RoutePoint(
                    color = Amber,
                    label = "PICKUP",
                    place = contract.pickup.label,
                    detail = "Deadline ${deadlineLabel(contract.deadlineTick, currentTick)}",
                )
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .size(width = 1.dp, height = 18.dp)
                        .background(Hairline)
                )
                RoutePoint(
                    color = Mint,
                    label = "DROPOFF",
                    place = contract.dropoff.label,
                    detail = "${km(contract.estimatedKm)} routed",
                )
            }
        }

        item("facts") {
            CityCard {
                KeyValueRow("Payload", "${contract.payload.weightKg.toInt()} kg")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Handling", if (contract.payload.requiresRefrigeration) "Refrigerated" else "Ambient")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Status", contract.status.shortLabel, valueColor = Sky)
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Distance", km(contract.estimatedKm), mono = true)
            }
        }

        item("matches_header") {
            SectionHeader(
                title = if (contract.isActive) "Assigned asset" else "Best matches",
                overline = "Matching engine",
                subtitle = if (contract.isActive) null
                else "Deterministic score at ${scorePercent(MatchingEngine.MIN_DISPATCH_THRESHOLD)} minimum",
            )
        }

        if (contract.isActive) {
            val vehicle = vehicles.find { it.id == contract.assignedVehicleId }
            val driver = staff[contract.assignedDriverId]
            item("assigned") {
                CityCard {
                    if (vehicle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.LocalShipping, Mint, size = 38)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vehicle.plate, style = MaterialTheme.typography.titleSmall, color = Paper)
                                Text(
                                    "${vehicle.vehicleClass.label} · ${vehicle.status.shortLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate,
                                )
                            }
                            Text(driver?.name ?: "Unassigned driver", style = MaterialTheme.typography.labelMedium, color = Iris)
                        }
                        if (vehicle.routeKm > 0) {
                            Spacer(Modifier.height(12.dp))
                            MeterRow(
                                label = "Leg progress",
                                fraction = meterFraction(vehicle.legProgress * 100.0),
                                valueLabel = km(vehicle.remainingKm) + " left",
                                color = Mint,
                            )
                        }
                    } else {
                        Text("Vehicle not in snapshot", style = MaterialTheme.typography.bodyMedium, color = Slate)
                    }
                }
            }
        } else if (ranked.isEmpty()) {
            item("no_vehicles") {
                EmptyState(
                    title = "No idle vehicles",
                    message = "Every asset is on the road, resting or in the workshop. Free a vehicle or buy another.",
                    icon = Icons.Rounded.LocalShipping,
                )
            }
        } else {
            items(ranked, key = { it.first.id }) { (vehicle, score) ->
                val driver = staff[vehicle.assignedDriverId]
                val eligible = score.suitabilityScore > 0.0
                CityCard(
                    containerColor = SurfaceRaised,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(vehicle.plate, style = MaterialTheme.typography.titleSmall, color = Paper)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    vehicle.vehicleClass.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                )
                            }
                            Text(
                                (driver?.name ?: "No driver") + " · ${vehicle.fuel.toInt()}% fuel",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${scorePercent(score.totalScore)}",
                                style = NumericStyle.copy(fontSize = 18.sp),
                                color = when {
                                    score.totalScore >= MatchingEngine.MIN_DISPATCH_THRESHOLD -> Mint
                                    else -> Amber
                                },
                            )
                            Text("score", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    MeterRow(
                        label = "Fit",
                        fraction = score.totalScore.toFloat().coerceIn(0f, 1f),
                        valueLabel = if (!eligible) "Not suitable" else "${percentOf(score.suitabilityScore)} suitability",
                        color = if (eligible) Mint else Coral,
                    )
                    if (!eligible) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "This asset cannot carry the payload or refrigeration requirement.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Coral,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Dispatch",
                        onClick = { onAssign(vehicle.id) },
                        enabled = eligible,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutePoint(color: Color, label: String, place: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(place, style = MaterialTheme.typography.titleSmall, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Slate)
        }
    }
}

private fun percentOf(value: Double): String = String.format(Locale.US, "%.0f%%", value * 100)

private fun contractIcon(type: ContractType): ImageVector = when (type) {
    ContractType.RIDESHARE -> Icons.Rounded.Person
    ContractType.FOOD -> Icons.Rounded.Restaurant
    ContractType.PARCEL -> Icons.Rounded.Inventory2
    ContractType.CARGO -> Icons.Rounded.LocalShipping
}
