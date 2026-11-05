package com.example.ui.staff

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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.StaffStatus
import com.example.model.Vehicle
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
fun StaffScreen(
    staffList: List<Staff>,
    vehicles: List<Vehicle>,
    playerCash: Double,
    onHireStaff: (name: String, role: StaffRole, skill: Int) -> Unit,
    onToggleShift: (staffId: String) -> Unit,
    onAssignVehicle: (staffId: String, vehicleId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showHireDialog by remember { mutableStateOf(false) }

    val filteredStaff = when (selectedTab) {
        0 -> staffList
        1 -> staffList.filter { it.role == StaffRole.DRIVER }
        2 -> staffList.filter { it.role == StaffRole.DISPATCHER }
        3 -> staffList.filter { it.role == StaffRole.MECHANIC }
        else -> staffList.filter { it.role == StaffRole.MANAGER }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CityDarkNavy)
            .padding(16.dp)
            .testTag("staff_screen_root")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Personnel & Roster",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimaryDark
                )
                Text(
                    text = "${staffList.size} Total Employees • Daily Payroll: $${staffList.sumOf { it.salaryPerDay }.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }

            Button(
                onClick = { showHireDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("hire_staff_btn")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = CitySurfaceNavy, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hire", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            listOf("All (${staffList.size})", "Drivers", "Dispatchers", "Mechanics", "Managers").forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredStaff, key = { it.id }) { member ->
                val assignedVeh = vehicles.find { it.id == member.assignedVehicleId }

                StaffCard(
                    member = member,
                    assignedVehicle = assignedVeh,
                    onToggleShift = { onToggleShift(member.id) }
                )
            }
        }
    }

    // Hire Staff Modal
    if (showHireDialog) {
        val candidates = listOf(
            Triple("Thabo Mthembu", StaffRole.DRIVER, 7),
            Triple("Lerato Sithole", StaffRole.DRIVER, 9),
            Triple("Johan De Bruyn", StaffRole.MECHANIC, 8),
            Triple("Amina Patel", StaffRole.DISPATCHER, 8),
            Triple("Clive Van Zyl", StaffRole.MANAGER, 6)
        )

        AlertDialog(
            onDismissRequest = { showHireDialog = false },
            containerColor = CitySurfaceNavy,
            title = { Text("Recruitment Hall", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Recruitment Fee: $350 • Available Cash: $${playerCash.toInt()}",
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(candidates) { (name, role, skill) ->
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
                                        Text(name, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${role.name} • Skill Rating: $skill/10", color = CyanNeon, fontSize = 11.sp)
                                        Text("Wage: $${120 + skill * 5}/day", color = TextSecondaryDark, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            onHireStaff(name, role, skill)
                                            showHireDialog = false
                                        },
                                        enabled = playerCash >= 350.0,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)
                                    ) {
                                        Text("Hire", color = CitySurfaceNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showHireDialog = false }) {
                    Text("Close", color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun StaffCard(
    member: Staff,
    assignedVehicle: Vehicle?,
    onToggleShift: () -> Unit
) {
    val isFatigued = member.fatigue >= 80.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_card_${member.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFatigued) CrimsonError.copy(alpha = 0.8f) else CityBorderNavy
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
                            getRoleIcon(member.role),
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimaryDark
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${member.role.name} • Wage: $${member.salaryPerDay.toInt()}/day",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }

                Badge(
                    containerColor = when (member.status) {
                        StaffStatus.ON_SHIFT -> EmeraldSuccess
                        StaffStatus.RESTING -> AmberWarning
                        else -> CityCardNavy
                    }
                ) {
                    Text(
                        text = member.status.name.replace("_", " "),
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Skill Stars & Assigned Vehicle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Skill:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    repeat(member.skill) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(12.dp))
                    }
                    Text(" (${member.skill}/10)", fontSize = 11.sp, color = TextMutedDark)
                }

                if (assignedVehicle != null) {
                    Text(
                        text = "Assigned: ${assignedVehicle.plate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanNeon,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fatigue Progress Bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fatigue (${member.fatigue.toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFatigued) CrimsonError else TextSecondaryDark,
                    modifier = Modifier.width(90.dp)
                )
                LinearProgressIndicator(
                    progress = { (member.fatigue / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = if (isFatigued) CrimsonError else AmberWarning,
                    trackColor = CityCardNavy,
                )
            }

            if (isFatigued) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠ Extreme fatigue! Driver at high risk of accidents and forced rest.",
                    color = CrimsonError,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shift toggle button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onToggleShift,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        if (member.status == StaffStatus.ON_SHIFT) Icons.Default.Bedtime else Icons.Default.Engineering,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = CyanNeon
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (member.status == StaffStatus.ON_SHIFT) "Send on Break" else "Call to Shift",
                        fontSize = 11.sp,
                        color = TextPrimaryDark
                    )
                }
            }
        }
    }
}

private fun getRoleIcon(role: StaffRole): ImageVector {
    return when (role) {
        StaffRole.DRIVER -> Icons.Default.DirectionsCar
        StaffRole.DISPATCHER -> Icons.Default.HeadsetMic
        StaffRole.MECHANIC -> Icons.Default.Build
        StaffRole.MANAGER -> Icons.Default.ManageAccounts
    }
}
