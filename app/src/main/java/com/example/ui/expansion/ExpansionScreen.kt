package com.example.ui.expansion

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CityZone
import com.example.model.VehicleClass
import com.example.simulation.MonteCarloInput
import com.example.simulation.MonteCarloOutput
import com.example.simulation.MonteCarloSimulator
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

@Composable
fun ExpansionScreen(
    zones: List<CityZone>,
    playerCash: Double,
    currentTick: Long,
    onUnlockZone: (zoneId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CityDarkNavy)
            .padding(16.dp)
            .testTag("expansion_screen_root")
    ) {
        // Header
        Column {
            Text(
                text = "Territory Expansion & Market Growth",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimaryDark
            )
            Text(
                text = "Unlock adjacent zones to scale demand and run 500-run Monte Carlo projections.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )
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
                text = { Text("City Zones (${zones.count { it.unlocked }}/${zones.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Monte Carlo Simulator") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedTab) {
            0 -> {
                // Zone List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(zones, key = { it.zoneId }) { zone ->
                        ZoneCard(
                            zone = zone,
                            playerCash = playerCash,
                            onUnlock = { onUnlockZone(zone.zoneId) }
                        )
                    }
                }
            }

            1 -> {
                // Monte Carlo Expansion Strategy Simulator
                MonteCarloSimulatorPanel(zones, playerCash)
            }
        }
    }
}

@Composable
private fun ZoneCard(
    zone: CityZone,
    playerCash: Double,
    onUnlock: () -> Unit
) {
    val canAfford = playerCash >= zone.unlockCost

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("zone_card_${zone.zoneId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (zone.unlocked) CyanNeon.copy(alpha = 0.4f) else CityBorderNavy
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (zone.unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (zone.unlocked) CyanNeon else TextMutedDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = zone.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimaryDark
                    )
                }

                Badge(
                    containerColor = if (zone.unlocked) EmeraldSuccess else CityCardNavy
                ) {
                    Text(
                        text = if (zone.unlocked) "OPERATIONAL" else "LOCKED",
                        fontSize = 10.sp,
                        color = if (zone.unlocked) CitySurfaceNavy else TextSecondaryDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Demand Multiplier", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("${zone.demandMultiplier}x", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                }
                Column {
                    Text("Traffic Index", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("${(zone.trafficIndex * 100).toInt()}% Congestion", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AmberWarning)
                }
                Column {
                    Text("Completed Jobs", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("${zone.completedContracts}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!zone.unlocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Expansion License Fee", fontSize = 11.sp, color = TextSecondaryDark)
                        Text(
                            text = "$${String.format("%,.0f", zone.unlockCost)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canAfford) EmeraldSuccess else CrimsonError
                        )
                    }

                    Button(
                        onClick = onUnlock,
                        enabled = canAfford,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                        modifier = Modifier.testTag("unlock_zone_btn_${zone.zoneId}")
                    ) {
                        Text("Unlock Zone", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonteCarloSimulatorPanel(zones: List<CityZone>, currentCash: Double) {
    val lockedZones = zones.filter { !it.unlocked }
    val candidateZone = lockedZones.firstOrNull() ?: zones.first()

    var addVans by remember { mutableIntStateOf(2) }
    var addDrivers by remember { mutableIntStateOf(2) }
    var marketingSpend by remember { mutableFloatStateOf(2500f) }

    var simulationResult by remember { mutableStateOf<MonteCarloOutput?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
                border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoGraph, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Expansion Strategy Parameters",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimaryDark
                        )
                    }
                    Text(
                        text = "Target Area: ${candidateZone.name} (License: $${candidateZone.unlockCost.toInt()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanNeon
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Vehicle Slider
                    Text("Additional Vans to Procure: $addVans (Cost: $${addVans * 14000})", fontSize = 12.sp, color = TextPrimaryDark)
                    Slider(
                        value = addVans.toFloat(),
                        onValueChange = { addVans = it.toInt() },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon)
                    )

                    // Driver slider
                    Text("Driver Hires: $addDrivers (Recruitment: $${addDrivers * 350})", fontSize = 12.sp, color = TextPrimaryDark)
                    Slider(
                        value = addDrivers.toFloat(),
                        onValueChange = { addDrivers = it.toInt() },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = EmeraldSuccess, activeTrackColor = EmeraldSuccess)
                    )

                    // Marketing Spend
                    Text("Marketing Campaign: $${marketingSpend.toInt()}", fontSize = 12.sp, color = TextPrimaryDark)
                    Slider(
                        value = marketingSpend,
                        onValueChange = { marketingSpend = it },
                        valueRange = 0f..10000f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = AmberWarning, activeTrackColor = AmberWarning)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val input = MonteCarloInput(
                                currentCash = currentCash,
                                targetZone = candidateZone,
                                additionalVehicles = mapOf(VehicleClass.VAN to addVans),
                                additionalDrivers = addDrivers,
                                marketingSpend = marketingSpend.toDouble()
                            )
                            simulationResult = MonteCarloSimulator.runSimulation(input)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("run_monte_carlo_btn"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)
                    ) {
                        Text("Run 500 Monte Carlo Iterations (30 Days)", color = CitySurfaceNavy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Results Card
        if (simulationResult != null) {
            val res = simulationResult!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("monte_carlo_results_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "30-Day Projected Cash Flow (Percentiles)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "P10 (Worst 10%) • P50 (Expected) • P90 (Best 10%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Projection Chart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(CityCardNavy, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                val maxVal = (res.dailyProjections.maxOfOrNull { it.p90Cash } ?: 60000.0).coerceAtLeast(30000.0)
                                val minVal = 0.0

                                fun yPos(v: Double) = (h - ((v / maxVal).toFloat() * (h - 10f))).coerceIn(5f, h - 5f)
                                fun xPos(day: Int) = (day.toFloat() / 30f) * w

                                // P90 Path (Cyan Neon)
                                val p90Path = Path().apply {
                                    moveTo(xPos(0), yPos(res.dailyProjections[0].p90Cash))
                                    for (d in 1..30) {
                                        lineTo(xPos(d), yPos(res.dailyProjections[d].p90Cash))
                                    }
                                }
                                drawPath(p90Path, color = CyanNeon, style = Stroke(width = 3f, cap = StrokeCap.Round))

                                // P50 Path (Emerald)
                                val p50Path = Path().apply {
                                    moveTo(xPos(0), yPos(res.dailyProjections[0].p50Cash))
                                    for (d in 1..30) {
                                        lineTo(xPos(d), yPos(res.dailyProjections[d].p50Cash))
                                    }
                                }
                                drawPath(p50Path, color = EmeraldSuccess, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

                                // P10 Path (Amber / Crimson)
                                val p10Path = Path().apply {
                                    moveTo(xPos(0), yPos(res.dailyProjections[0].p10Cash))
                                    for (d in 1..30) {
                                        lineTo(xPos(d), yPos(res.dailyProjections[d].p10Cash))
                                    }
                                }
                                drawPath(p10Path, color = AmberWarning, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Metrics Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("P50 Expected Capital", fontSize = 11.sp, color = TextSecondaryDark)
                                Text("$${String.format("%,.0f", res.p50FinalCash)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                            }
                            Column {
                                Text("Break-Even Day", fontSize = 11.sp, color = TextSecondaryDark)
                                Text(if (res.breakEvenDay != null) "Day ${res.breakEvenDay}" else "N/A", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                            }
                            Column {
                                Text("Insolvency Risk", fontSize = 11.sp, color = TextSecondaryDark)
                                Text("${(res.probabilityOfInsolvency * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (res.probabilityOfInsolvency > 0.15) CrimsonError else EmeraldSuccess)
                            }
                        }
                    }
                }
            }
        }
    }
}
