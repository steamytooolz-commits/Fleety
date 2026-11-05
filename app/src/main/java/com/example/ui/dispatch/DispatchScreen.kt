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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Contract
import com.example.model.ContractStatus
import com.example.model.ContractType
import com.example.model.MatchingScore
import com.example.model.Staff
import com.example.model.Vehicle
import com.example.model.VehicleStatus
import com.example.simulation.MatchingEngine
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
import java.util.UUID

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
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inspectingContract by remember { mutableStateOf<Contract?>(null) }

    val openContracts = contracts.filter { it.status == ContractStatus.OPEN }
    val inProgressContracts = contracts.filter { it.status == ContractStatus.ASSIGNED || it.status == ContractStatus.IN_PROGRESS }
    val completedContracts = contracts.filter { it.status == ContractStatus.COMPLETED || it.status == ContractStatus.FAILED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CityDarkNavy)
            .padding(16.dp)
            .testTag("dispatch_screen_root")
    ) {
        // Header with Auto-Dispatch Switch and Manual Generate Contract
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dispatch Command",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimaryDark
                )
                Text(
                    text = "${openContracts.size} Open Contracts waiting for assignment",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Auto-Dispatch Toggle
                IconButton(
                    onClick = onToggleAutoDispatch,
                    modifier = Modifier.testTag("toggle_auto_dispatch_btn")
                ) {
                    Icon(
                        if (autoDispatchEnabled) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                        contentDescription = "Toggle Auto-Dispatch",
                        tint = if (autoDispatchEnabled) CyanNeon else TextMutedDark,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onSpawnContract,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("spawn_contract_btn")
                ) {
                    Text(text = "+ Job", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
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
                text = { Text("Open (${openContracts.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Active (${inProgressContracts.size})") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Log (${completedContracts.size})") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val displayedContracts = when (selectedTab) {
            0 -> openContracts
            1 -> inProgressContracts
            else -> completedContracts
        }

        if (displayedContracts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        tint = TextMutedDark,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedTab == 0) "No open contracts. Spawn or wait for zone demand." else "No contracts in this view.",
                        color = TextSecondaryDark,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedContracts, key = { it.id }) { contract ->
                    ContractCard(
                        contract = contract,
                        currentTick = currentTick,
                        onInspect = { inspectingContract = contract },
                        onAssign = { inspectingContract = contract }
                    )
                }
            }
        }
    }

    // Modal for Inspecting & Manual Assigning with Scoring Breakdown
    if (inspectingContract != null) {
        val contract = inspectingContract!!
        val idleVehicles = vehicles.filter { it.status == VehicleStatus.IDLE }

        AlertDialog(
            onDismissRequest = { inspectingContract = null },
            containerColor = CitySurfaceNavy,
            title = {
                Text(
                    text = "Contract: ${contract.type.label}",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Pickup: ${contract.pickup.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanNeon
                    )
                    Text(
                        text = "Dropoff: ${contract.dropoff.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldSuccess
                    )
                    Text(
                        text = "Payload: ${contract.payload.description} (${contract.payload.weightKg.toInt()} kg${if (contract.payload.requiresRefrigeration) ", REFRIGERATED REQ." else ""})",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning
                    )
                    Text(
                        text = "Reward: $${contract.reward} • Deadline: Tick #${contract.deadlineTick}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimaryDark
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Matching Engine Vehicle Scoring (Deterministic):",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimaryDark
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (idleVehicles.isEmpty()) {
                        Text(
                            text = "No idle vehicles available for dispatch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonError
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(idleVehicles) { v ->
                                val driver = v.assignedDriverId?.let { staff[it] }
                                val score = MatchingEngine.scoreVehicleForContract(v, driver, contract)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = CityCardNavy),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (score.totalScore >= 0.35) CyanNeon.copy(alpha = 0.5f) else CityBorderNavy
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${v.plate} (${v.vehicleClass.label})",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimaryDark
                                            )
                                            Text(
                                                text = "Score: ${String.format("%.2f", score.totalScore)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (score.totalScore >= 0.35) EmeraldSuccess else CrimsonError
                                            )
                                        }

                                        Text(
                                            text = "Driver: ${driver?.name ?: "None"} • Fuel: ${v.fuel.toInt()}% • Proximity: ${(score.proximityScore * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondaryDark,
                                            fontSize = 10.sp
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Button(
                                            onClick = {
                                                onManualAssign(contract.id, v.id)
                                                inspectingContract = null
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                                            enabled = score.suitabilityScore > 0.0
                                        ) {
                                            Text("Dispatch Vehicle", color = CitySurfaceNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { inspectingContract = null }) {
                    Text("Close", color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun ContractCard(
    contract: Contract,
    currentTick: Long,
    onInspect: () -> Unit,
    onAssign: () -> Unit
) {
    val isOverdue = currentTick > contract.deadlineTick && contract.status != ContractStatus.COMPLETED
    val typeColor = Color(contract.type.baseColorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
            .testTag("contract_card_${contract.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
        border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
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
                            .size(10.dp)
                            .background(typeColor, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = contract.type.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimaryDark
                    )
                }

                Badge(
                    containerColor = when (contract.status) {
                        ContractStatus.OPEN -> CyanNeon.copy(alpha = 0.2f)
                        ContractStatus.ASSIGNED, ContractStatus.IN_PROGRESS -> EmeraldSuccess.copy(alpha = 0.2f)
                        ContractStatus.COMPLETED -> CityCardNavy
                        ContractStatus.FAILED -> CrimsonError.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = contract.status.name,
                        fontSize = 10.sp,
                        color = when (contract.status) {
                            ContractStatus.OPEN -> CyanNeon
                            ContractStatus.ASSIGNED, ContractStatus.IN_PROGRESS -> EmeraldSuccess
                            ContractStatus.COMPLETED -> TextSecondaryDark
                            ContractStatus.FAILED -> CrimsonError
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Route points
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${contract.pickup.label}  ➔  ${contract.dropoff.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimaryDark,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${contract.payload.description} (${contract.payload.weightKg.toInt()} kg)${if (contract.payload.requiresRefrigeration) " • REFRIGERATION REQUIRED" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+$${String.format("%,.0f", contract.reward)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = EmeraldSuccess
                )

                if (contract.status == ContractStatus.OPEN) {
                    Button(
                        onClick = onAssign,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dispatch_job_btn_${contract.id}")
                    ) {
                        Text("Match & Dispatch", color = CitySurfaceNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (contract.status == ContractStatus.IN_PROGRESS || contract.status == ContractStatus.ASSIGNED) {
                    Text(
                        text = "Assigned: ${contract.assignedVehicleId ?: "Vehicle"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanNeon
                    )
                }
            }
        }
    }
}
