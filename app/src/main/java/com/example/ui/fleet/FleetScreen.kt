package com.example.ui.fleet

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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import com.example.model.shortLabel
import com.example.ui.components.CityCard
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterPills
import com.example.ui.components.IconTile
import com.example.ui.components.KeyValueRow
import com.example.ui.components.MeterRow
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.components.StatusChip
import com.example.ui.components.compactMoney
import com.example.ui.components.km
import com.example.ui.components.meterFraction
import com.example.ui.components.money
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
import com.example.ui.theme.Violet
import kotlin.math.exp

private enum class FleetFilter(val label: String) {
    ALL("All"),
    READY("Ready"),
    WORKING("Working"),
    ATTENTION("Attention"),
}

/**
 * Garage view: telemetry for every asset, the two actions that keep them earning (fuel and
 * maintenance), driver assignment and the dealer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetScreen(
    vehicles: List<Vehicle>,
    staff: Map<String, Staff>,
    playerCash: Double,
    onPurchaseVehicle: (VehicleClass) -> Unit,
    onRefuelVehicle: (vehicleId: String) -> Unit,
    onServiceVehicle: (vehicleId: String) -> Unit,
    onAssignDriver: (staffId: String, vehicleId: String?) -> Unit,
    fuelPrice: Double = 1.15,
    fuelReserve: Double = 0.0,
    onBuyFuel: (litres: Double) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    var detailVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var showDealer by remember { mutableStateOf(false) }
    var driverPickerFor by remember { mutableStateOf<Vehicle?>(null) }
    val dealerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val ready = vehicles.filter { it.status == VehicleStatus.IDLE }
    val working = vehicles.filter { it.isWorking }
    val attention = vehicles.filter { it.fuel < 20.0 || it.condition < 35.0 || it.isBroken }
    val displayed = when (FleetFilter.entries[filterIndex]) {
        FleetFilter.ALL -> vehicles
        FleetFilter.READY -> ready
        FleetFilter.WORKING -> working
        FleetFilter.ATTENTION -> attention
    }
    val avgFuel = if (vehicles.isEmpty()) 0 else vehicles.map { it.fuel }.average().toInt()
    val avgCondition = if (vehicles.isEmpty()) 0 else vehicles.map { it.condition }.average().toInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("fleet_screen_root"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("GARAGE & TELEMETRY", style = MaterialTheme.typography.labelSmall, color = Mint)
                    Spacer(Modifier.height(2.dp))
                    Text("Fleet", style = MaterialTheme.typography.headlineSmall, color = Paper)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${vehicles.size} assets · ${money(playerCash)} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                }
                Spacer(Modifier.width(10.dp))
                PrimaryButton(text = "Acquire", onClick = { showDealer = true })
            }
        }

        item("summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Ready",
                    value = "${ready.size}",
                    caption = "${working.size} on the road",
                    icon = Icons.Rounded.LocalShipping,
                    accent = Mint,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Attention",
                    value = "${attention.size}",
                    caption = if (attention.isEmpty()) "All healthy" else "Fuel or condition",
                    icon = Icons.Rounded.WarningAmber,
                    accent = if (attention.isEmpty()) Iris else Coral,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item("averages") {
            CityCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FLEET AVERAGES", style = MaterialTheme.typography.labelSmall, color = Slate)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("Fuel ", style = MaterialTheme.typography.bodySmall, color = Muted)
                            Text("$avgFuel%", style = NumericStyle, color = if (avgFuel < 30) Amber else Paper)
                            Spacer(Modifier.width(14.dp))
                            Text("Health ", style = MaterialTheme.typography.bodySmall, color = Muted)
                            Text("$avgCondition%", style = NumericStyle, color = if (avgCondition < 50) Coral else Paper)
                        }
                    }
                    Text(
                        "${vehicles.sumOf { it.mileage }.toInt()} km",
                        style = NumericStyle,
                        color = Slate,
                    )
                }
            }
        }

        item("depot") {
            val depotPrice = fuelPrice * 0.92
            val room = (800.0 - fuelReserve).coerceAtLeast(0.0)
            val litres = minOf(200.0, room)
            CityCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(icon = Icons.Rounded.LocalGasStation, tint = Iris, size = 36)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DEPOT RESERVE", style = MaterialTheme.typography.labelSmall, color = Slate)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${fuelReserve.toInt()} L", style = NumericStyle.copy(fontSize = 16.sp), color = Paper)
                            Text(
                                " · ${money(depotPrice, decimals = true)}/L bulk (pump ${money(fuelPrice, decimals = true)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted,
                            )
                        }
                    }
                    SecondaryButton(
                        text = if (litres >= 1.0) "Buy ${litres.toInt()} L" else "Full",
                        onClick = { onBuyFuel(200.0) },
                        enabled = litres >= 1.0,
                    )
                }
            }
        }

        item("filters") {
            FilterPills(
                items = FleetFilter.entries.map { filter ->
                    when (filter) {
                        FleetFilter.ALL -> "All ${vehicles.size}"
                        FleetFilter.READY -> "Ready ${ready.size}"
                        FleetFilter.WORKING -> "Working ${working.size}"
                        FleetFilter.ATTENTION -> "Attention ${attention.size}"
                    }
                },
                selectedIndex = filterIndex,
                onSelect = { filterIndex = it },
            )
        }

        if (displayed.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = if (vehicles.isEmpty()) "No vehicles yet" else "Nothing in this filter",
                    message = if (vehicles.isEmpty()) "Acquire your first asset to start moving cargo." else "Try another filter — every asset is accounted for.",
                    icon = Icons.Rounded.LocalShipping,
                    actionLabel = if (vehicles.isEmpty()) "Open dealer" else null,
                    onAction = if (vehicles.isEmpty()) ({ showDealer = true }) else null,
                )
            }
        }

        items(displayed, key = { it.id }) { vehicle ->
            val driver = staff[vehicle.assignedDriverId]
            VehicleCard(
                vehicle = vehicle,
                driver = driver,
                onClick = { detailVehicle = vehicle },
                onRefuel = { onRefuelVehicle(vehicle.id) },
                onService = { onServiceVehicle(vehicle.id) },
            )
        }
    }

    if (showDealer) {
        ModalBottomSheet(
            onDismissRequest = { showDealer = false },
            sheetState = dealerState,
            containerColor = SurfaceBase,
            contentColor = Paper,
            dragHandle = { SheetHandle() },
        ) {
            DealerSheet(
                cash = playerCash,
                onBuy = { cls ->
                    onPurchaseVehicle(cls)
                    showDealer = false
                },
            )
        }
    }

    val detail = detailVehicle
    if (detail != null) {
        ModalBottomSheet(
            onDismissRequest = { detailVehicle = null },
            sheetState = detailState,
            containerColor = SurfaceBase,
            contentColor = Paper,
            dragHandle = { SheetHandle() },
        ) {
            VehicleDetailSheet(
                vehicle = detail,
                driver = staff[detail.assignedDriverId],
                onRefuel = { onRefuelVehicle(detail.id); detailVehicle = null },
                onService = { onServiceVehicle(detail.id); detailVehicle = null },
                onPickDriver = { driverPickerFor = detail },
            )
        }
    }

    val picker = driverPickerFor
    if (picker != null) {
        DriverPickerDialog(
            vehicle = picker,
            staff = staff.values.filter { it.role == StaffRole.DRIVER },
            onSelect = { staffId ->
                onAssignDriver(staffId, picker.id)
                driverPickerFor = null
            },
            onUnassign = {
                picker.assignedDriverId?.let { onAssignDriver(it, null) }
                driverPickerFor = null
            },
            onDismiss = { driverPickerFor = null },
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 12.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(PillShape)
            .background(Hairline)
    )
}

@Composable
private fun VehicleCard(
    vehicle: Vehicle,
    driver: Staff?,
    onClick: () -> Unit,
    onRefuel: () -> Unit,
    onService: () -> Unit,
) {
    val risk = breakdownRisk(vehicle)
    val fuelColor = when {
        vehicle.fuel < 20 -> Coral
        vehicle.fuel < 45 -> Amber
        else -> Mint
    }
    val conditionColor = when {
        vehicle.condition < 35 -> Coral
        vehicle.condition < 65 -> Amber
        else -> Iris
    }
    val statusColor = when (vehicle.status) {
        VehicleStatus.BREAKDOWN -> Coral
        VehicleStatus.IDLE -> Slate
        VehicleStatus.MAINTENANCE, VehicleStatus.FORCED_REST -> Amber
        else -> Mint
    }

    CityCard(
        onClick = onClick,
        accent = if (risk > 0.35) Coral else null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = classIcon(vehicle.vehicleClass), tint = classColor(vehicle.vehicleClass), size = 40)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vehicle.plate, style = MaterialTheme.typography.titleSmall, color = Paper)
                    Spacer(Modifier.width(6.dp))
                    StatusChip(vehicle.status.shortLabel, statusColor)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${vehicle.vehicleClass.label} · ${driver?.name ?: "No driver assigned"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (driver == null) Amber else Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.Speed,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        MeterRow(
            label = "Fuel",
            fraction = meterFraction(vehicle.fuel),
            valueLabel = "${vehicle.fuel.toInt()}%",
            color = fuelColor,
        )
        Spacer(Modifier.height(10.dp))
        MeterRow(
            label = "Condition",
            fraction = meterFraction(vehicle.condition),
            valueLabel = "${vehicle.condition.toInt()}%",
            color = conditionColor,
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                km(vehicle.mileage),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
            if (vehicle.routeKm > 0 && vehicle.isWorking) {
                Spacer(Modifier.width(8.dp))
                Text("·", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${km(vehicle.remainingKm)} to go",
                    style = MaterialTheme.typography.labelSmall,
                    color = Sky,
                )
            }
            Spacer(Modifier.weight(1f))
            if (risk > 0.35) {
                Text(
                    "BREAKDOWN RISK ${(risk * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Coral,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Refuel ${money(refuelCost(vehicle))}",
                icon = Icons.Rounded.LocalGasStation,
                onClick = onRefuel,
                enabled = vehicle.fuel < 99.0,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = if (vehicle.status == VehicleStatus.MAINTENANCE) "In bay ${vehicle.serviceTicks}c"
                    else "Workshop ${money(198.0)}",
                icon = Icons.Rounded.Build,
                onClick = onService,
                enabled = vehicle.status == VehicleStatus.IDLE && vehicle.condition < 99.0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleDetailSheet(
    vehicle: Vehicle,
    driver: Staff?,
    onRefuel: () -> Unit,
    onService: () -> Unit,
    onPickDriver: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(classIcon(vehicle.vehicleClass), classColor(vehicle.vehicleClass), size = 46)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(vehicle.plate, style = MaterialTheme.typography.titleLarge, color = Paper)
                    Text(
                        "${vehicle.vehicleClass.label} · ${vehicle.status.shortLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                }
                StatusChip(
                    if (vehicle.isWorking) "On duty" else "Available",
                    if (vehicle.isWorking) Mint else Slate,
                )
            }
        }

        item("meters") {
            CityCard {
                MeterRow("Fuel tank", meterFraction(vehicle.fuel), "${vehicle.fuel.toInt()}%", if (vehicle.fuel < 20) Coral else Amber)
                Spacer(Modifier.height(12.dp))
                MeterRow("Condition", meterFraction(vehicle.condition), "${vehicle.condition.toInt()}%", if (vehicle.condition < 35) Coral else Iris)
                Spacer(Modifier.height(12.dp))
                MeterRow(
                    "Odometer wear",
                    meterFraction((vehicle.mileage / 25000.0 * 100).coerceAtMost(100.0)),
                    km(vehicle.mileage),
                    Violet,
                )
            }
        }

        item("driver") {
            CityCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ASSIGNED DRIVER", style = MaterialTheme.typography.labelSmall, color = Slate)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            driver?.name ?: "No driver",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (driver == null) Amber else Paper,
                        )
                        if (driver != null) {
                            Text(
                                "Skill ${driver.skill}/10 · Fatigue ${driver.fatigue.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate,
                            )
                        }
                    }
                    SecondaryButton(
                        text = if (driver == null) "Assign" else "Change",
                        icon = Icons.Rounded.PersonOutline,
                        onClick = onPickDriver,
                    )
                }
            }
        }

        item("specs") {
            CityCard {
                KeyValueRow("Payload capacity", "${vehicle.vehicleClass.payloadCapKg.toInt()} kg")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Tank", "${vehicle.vehicleClass.tankSize.toInt()} L")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Daily upkeep", money(vehicle.vehicleClass.dailyUpkeep))
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Breakdown risk", "${(breakdownRisk(vehicle) * 100).toInt()}%", valueColor = if (breakdownRisk(vehicle) > 0.35) Coral else Mint)
            }
        }

        item("actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Refuel ${money(refuelCost(vehicle))}",
                    onClick = onRefuel,
                    enabled = vehicle.fuel < 99.0,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Full service ${money(220.0)}",
                    onClick = onService,
                    enabled = vehicle.condition < 100.0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DriverPickerDialog(
    vehicle: Vehicle,
    staff: List<Staff>,
    onSelect: (String) -> Unit,
    onUnassign: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Text("Assign driver", style = MaterialTheme.typography.titleLarge, color = Paper)
                Text(vehicle.plate, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        },
        text = {
            Column {
                if (staff.isEmpty()) {
                    Text(
                        "No drivers on the payroll. Hire one from Company → Personnel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate,
                    )
                }
                staff.take(6).forEach { driver ->
                    val busyElsewhere = driver.assignedVehicleId != null && driver.assignedVehicleId != vehicle.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !busyElsewhere) { onSelect(driver.id) }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Iris.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(driver.initials, style = MaterialTheme.typography.labelMedium, color = Iris, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(driver.name, style = MaterialTheme.typography.titleSmall, color = Paper)
                            Text(
                                "Skill ${driver.skill}/10 · ${driver.status.name.lowercase().replace('_', ' ')}" +
                                    if (busyElsewhere) " · driving ${driver.assignedVehicleId}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (busyElsewhere) Muted else Slate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (driver.assignedVehicleId == vehicle.id) {
                            StatusChip("Current", Mint)
                        }
                    }
                }
            }
        },
        confirmButton = {
            SecondaryButton(text = "Unassign", onClick = onUnassign, enabled = vehicle.assignedDriverId != null)
        },
        dismissButton = {
            SecondaryButton(text = "Close", onClick = onDismiss)
        },
    )
}

@Composable
private fun DealerSheet(cash: Double, onBuy: (VehicleClass) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Column {
                Text("ACQUISITIONS", style = MaterialTheme.typography.labelSmall, color = Mint)
                Spacer(Modifier.height(2.dp))
                Text("Fleet dealer", style = MaterialTheme.typography.titleLarge, color = Paper)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Available capital ${money(cash)} · new assets arrive at base with a full tank",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }
        items(VehicleClass.entries.toList(), key = { it.name }) { cls ->
            val affordable = cash >= cls.purchasePrice
            CityCard(accent = classColor(cls), containerColor = SurfaceRaised) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(classIcon(cls), classColor(cls), size = 42)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cls.label, style = MaterialTheme.typography.titleSmall, color = Paper)
                        Text(
                            "${cls.payloadCapKg.toInt()} kg · ${cls.tankSize.toInt()} L · ${money(cls.dailyUpkeep)}/day",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            money(cls.purchasePrice),
                            style = NumericStyle.copy(fontSize = 15.sp),
                            color = if (affordable) Mint else Coral,
                        )
                        Spacer(Modifier.height(6.dp))
                        PrimaryButton(
                            text = "Buy",
                            onClick = { onBuy(cls) },
                            enabled = affordable,
                        )
                    }
                }
                if (cls.requiresRefrigeration) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = Sky, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Required for chilled food contracts",
                            style = MaterialTheme.typography.labelSmall,
                            color = Sky,
                        )
                    }
                }
            }
        }
    }
}

private fun classIcon(cls: VehicleClass): ImageVector = when (cls) {
    VehicleClass.SEDAN -> Icons.Rounded.LocalShipping
    VehicleClass.VAN -> Icons.Rounded.LocalShipping
    VehicleClass.TRUCK -> Icons.Rounded.LocalShipping
    VehicleClass.REFRIGERATED -> Icons.Rounded.AcUnit
}

private fun classColor(cls: VehicleClass): Color = when (cls) {
    VehicleClass.SEDAN -> Sky
    VehicleClass.VAN -> Mint
    VehicleClass.TRUCK -> Violet
    VehicleClass.REFRIGERATED -> Iris
}

private fun refuelCost(vehicle: Vehicle): Double = vehicle.vehicleClass.tankSize * 1.15 + 25.0

/** Same curve the tick service documents: risk climbs with mileage and falls with condition. */
private fun breakdownRisk(vehicle: Vehicle): Double {
    val condition = (vehicle.condition / 100.0).coerceAtLeast(0.05)
    return (1.0 - exp(-vehicle.mileage / (20000.0 * condition))).coerceIn(0.0, 1.0)
}
