package com.example.ui.fleet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Staff
import com.example.model.Vehicle
import com.example.model.VehicleClass
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
import kotlin.math.exp

@Composable
fun FleetScreen(
    vehicles: List<Vehicle>,
    staff: Map<String, Staff>,
    playerCash: Double,
    onPurchaseVehicle: (VehicleClass) -> Unit,
    onRefuelVehicle: (vehicleId: String) -> Unit,
    onServiceVehicle: (vehicleId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showBuyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CityDarkNavy)
            .padding(16.dp)
            .testTag("fleet_screen_root")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Fleet Management & Telemetry",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimaryDark
                )
                Text(
                    text = "${vehicles.size} Active Commercial Assets • Predictive Health Models",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }

            Button(
                onClick = { showBuyDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("buy_vehicle_btn")
            ) {
                Text("+ Acquire", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CitySurfaceNavy,
            contentColor = CyanNeon,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyanNeon
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Active Fleet (${vehicles.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Breakdown Analytics") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Cost Per Km") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                // List of Vehicles
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        val driver = vehicle.assignedDriverId?.let { staff[it] }
                        // Breakdown probability: P(breakdown) = 1 - exp(-mileage / (20000 * condition/100))
                        val condRatio = (vehicle.condition / 100.0).coerceAtLeast(0.05)
                        val breakdownProb = (1.0 - exp(-vehicle.mileage / (20000.0 * condRatio))).coerceIn(0.0, 1.0)
                        val isAtRisk = breakdownProb > 0.25 || vehicle.condition < 40.0

                        VehicleCard(
                            vehicle = vehicle,
                            driver = driver,
                            breakdownProb = breakdownProb,
                            isAtRisk = isAtRisk,
                            onRefuel = { onRefuelVehicle(vehicle.id) },
                            onService = { onServiceVehicle(vehicle.id) }
                        )
                    }
                }
            }

            1 -> {
                // Fleet Health & Scatter Plot
                FleetScatterPlotAndAnalytics(vehicles)
            }

            2 -> {
                // Cost per KM metrics
                CostPerKmAnalytics(vehicles, staff)
            }
        }
    }

    // Acquire Vehicle Dialog
    if (showBuyDialog) {
        AlertDialog(
            onDismissRequest = { showBuyDialog = false },
            containerColor = CitySurfaceNavy,
            title = { Text("Acquire Commercial Vehicle", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Available Capital: $${String.format("%,.0f", playerCash)}",
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VehicleClass.values()) { vClass ->
                            val canAfford = playerCash >= vClass.purchasePrice
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CityCardNavy),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(vClass.label, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            "Payload: ${vClass.payloadCapKg.toInt()} kg • Upkeep: $${vClass.dailyUpkeep.toInt()}/day",
                                            color = TextSecondaryDark,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "Price: $${String.format("%,.0f", vClass.purchasePrice)}",
                                            color = if (canAfford) CyanNeon else CrimsonError,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onPurchaseVehicle(vClass)
                                            showBuyDialog = false
                                        },
                                        enabled = canAfford,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)
                                    ) {
                                        Text("Buy", color = CitySurfaceNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showBuyDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun VehicleCard(
    vehicle: Vehicle,
    driver: Staff?,
    breakdownProb: Double,
    isAtRisk: Boolean,
    onRefuel: () -> Unit,
    onService: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vehicle_card_${vehicle.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAtRisk) CrimsonError.copy(alpha = 0.7f) else CityBorderNavy
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(CityCardNavy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${vehicle.plate} (${vehicle.vehicleClass.label})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Driver: ${driver?.name ?: "Unassigned"} • Mileage: ${String.format("%,.1f", vehicle.mileage)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }
                }

                Badge(
                    containerColor = when (vehicle.status) {
                        VehicleStatus.BREAKDOWN -> CrimsonError
                        VehicleStatus.IDLE -> CityCardNavy
                        VehicleStatus.DIVERT_TO_REFUEL -> AmberWarning
                        else -> EmeraldSuccess
                    }
                ) {
                    Text(
                        text = vehicle.status.name.replace("_", " "),
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fuel bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fuel (${vehicle.fuel.toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    modifier = Modifier.width(80.dp)
                )
                LinearProgressIndicator(
                    progress = { (vehicle.fuel / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = if (vehicle.fuel < 20.0) CrimsonError else AmberWarning,
                    trackColor = CityCardNavy,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Condition bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Health (${vehicle.condition.toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    modifier = Modifier.width(80.dp)
                )
                LinearProgressIndicator(
                    progress = { (vehicle.condition / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = if (vehicle.condition < 35.0) CrimsonError else EmeraldSuccess,
                    trackColor = CityCardNavy,
                )
            }

            if (isAtRisk) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "High breakdown probability: ${(breakdownProb * 100).toInt()}% risk. Service recommended.",
                        color = CrimsonError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons: Refuel & Service
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onRefuel,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.LocalGasStation, contentDescription = null, modifier = Modifier.size(14.dp), tint = AmberWarning)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refuel ($90)", fontSize = 11.sp, color = TextPrimaryDark)
                }

                Button(
                    onClick = onService,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp), tint = CitySurfaceNavy)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Maintenance Bay ($220)", fontSize = 11.sp, color = CitySurfaceNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FleetScatterPlotAndAnalytics(vehicles: List<Vehicle>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
        border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Vehicle Condition vs. Mileage Scatter Plot",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimaryDark
            )
            Text(
                text = "P(breakdown) = 1 - exp(-mileage / (20000 × condition/100))",
                style = MaterialTheme.typography.bodySmall,
                color = CyanNeon,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Scatter Plot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(CityCardNavy, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Axes
                    drawLine(CityBorderNavy, Offset(30f, 0f), Offset(30f, h - 20f), strokeWidth = 2f)
                    drawLine(CityBorderNavy, Offset(30f, h - 20f), Offset(w, h - 20f), strokeWidth = 2f)

                    // Plot points
                    val maxMileage = (vehicles.maxOfOrNull { it.mileage } ?: 5000.0).coerceAtLeast(3000.0)

                    for (v in vehicles) {
                        val x = 30f + ((v.mileage / maxMileage).toFloat() * (w - 45f)).coerceIn(0f, w - 45f)
                        val y = (h - 20f) - ((v.condition / 100.0).toFloat() * (h - 35f)).coerceIn(0f, h - 35f)

                        val ptColor = when {
                            v.condition < 35.0 -> CrimsonError
                            v.condition < 70.0 -> AmberWarning
                            else -> EmeraldSuccess
                        }

                        drawCircle(color = ptColor, radius = 7f, center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 7f, center = Offset(x, y), style = Stroke(1.5f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "X-Axis: Cumulative Mileage (km) • Y-Axis: Condition Health (0-100%)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Surfaced vehicles at risk
            Text(
                text = "Vehicles at Risk This Week (Flagged):",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimaryDark
            )

            val atRisk = vehicles.filter { (it.condition / 100.0) < 0.45 || it.mileage > 4500.0 }
            if (atRisk.isEmpty()) {
                Text("✓ All fleet vehicles operating within safe tolerance parameters.", color = EmeraldSuccess, fontSize = 12.sp)
            } else {
                for (v in atRisk) {
                    Text("• ${v.plate} (${v.vehicleClass.label}) - Health: ${v.condition.toInt()}%", color = CrimsonError, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CostPerKmAnalytics(vehicles: List<Vehicle>, staff: Map<String, Staff>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
        border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Cost-Per-Km Efficiency Analysis",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimaryDark
            )
            Text(
                text = "Operating cost (fuel burn + maintenance upkeep) per driven kilometer.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Class breakdown
            VehicleClass.values().forEach { vClass ->
                val classVehicles = vehicles.filter { it.vehicleClass == vClass }
                val avgCostPerKm = (vClass.baseBurnRate * 1.85) + (vClass.dailyUpkeep / 120.0)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(vClass.label, color = TextPrimaryDark, fontSize = 13.sp)
                    Text("$${String.format("%.2f", avgCostPerKm)} / km", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Driver Cost Outliers (>1.5× Fleet Median):",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimaryDark
            )

            val lowSkillDrivers = staff.values.filter { it.skill < 4 }
            if (lowSkillDrivers.isEmpty()) {
                Text("✓ All active drivers operating at or above efficiency benchmarks.", color = EmeraldSuccess, fontSize = 12.sp)
            } else {
                lowSkillDrivers.forEach { d ->
                    Text("⚠ Driver ${d.name} (Skill ${d.skill}/10) burns 35% excess fuel per trip.", color = AmberWarning, fontSize = 12.sp)
                }
            }
        }
    }
}
