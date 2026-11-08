package com.example.ui.company

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Campaign
import com.example.model.CityZone
import com.example.model.Contract
import com.example.model.Loan
import com.example.model.ContractStatus
import com.example.model.Player
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.StaffStatus
import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import com.example.model.shortLabel
import com.example.simulation.MonteCarloInput
import com.example.simulation.MonteCarloOutput
import com.example.simulation.MonteCarloSimulator
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
import com.example.ui.components.deadlineLabel
import com.example.ui.components.meterFraction
import com.example.ui.components.money
import com.example.ui.components.percent
import com.example.ui.components.signedMoney
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
import com.example.ui.theme.Sky
import com.example.ui.theme.Slate
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceHigh
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.Violet

private enum class CompanyTab(val label: String) {
    PERSONNEL("Personnel"),
    TERRITORY("Territory"),
    MARKETING("Marketing"),
    FINANCE("Finance"),
    LEDGER("Ledger"),
}

/**
 * Company hub: everything that is not minute-to-minute operations. Personnel, territory expansion
 * and the full financial ledger share one destination so the bottom bar stays at five entries.
 */
@Composable
fun CompanyScreen(
    player: Player,
    staff: List<Staff>,
    vehicles: List<Vehicle>,
    contracts: List<Contract>,
    transactions: List<Transaction>,
    zones: List<CityZone>,
    campaigns: List<Campaign>,
    currentTick: Long,
    onHireStaff: (name: String, role: StaffRole, skill: Int) -> Unit,
    onToggleShift: (staffId: String) -> Unit,
    onAssignDriver: (staffId: String, vehicleId: String?) -> Unit,
    onUnlockZone: (zoneId: String) -> Unit,
    onStartCampaign: (zoneId: String, budget: Double) -> Unit = { _, _ -> },
    onTakeLoan: (amount: Double) -> Unit = {},
    onRepayLoan: (amount: Double) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("company_screen_root"),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Text("COMPANY", style = MaterialTheme.typography.labelSmall, color = Mint)
            Spacer(Modifier.height(2.dp))
            Text("Back office", style = MaterialTheme.typography.headlineSmall, color = Paper)
            Spacer(Modifier.height(10.dp))
            FilterPills(
                items = listOf(
                    "Personnel ${staff.size}",
                    "Territory ${zones.count { it.unlocked }}/${zones.size}",
                    "Marketing ${campaigns.count { it.isActive }}",
                    "Finance",
                    "Ledger ${transactions.size}",
                ),
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
            )
        }

        Spacer(Modifier.height(12.dp))

        when (CompanyTab.entries[tabIndex]) {
            CompanyTab.PERSONNEL -> PersonnelTab(
                staff = staff,
                vehicles = vehicles,
                cash = player.cash,
                onHireStaff = onHireStaff,
                onToggleShift = onToggleShift,
                onAssignDriver = onAssignDriver,
            )

            CompanyTab.TERRITORY -> TerritoryTab(
                zones = zones,
                cash = player.cash,
                contracts = contracts,
                onUnlockZone = onUnlockZone,
            )

            CompanyTab.MARKETING -> MarketingTab(
                campaigns = campaigns,
                zones = zones,
                player = player,
                onStartCampaign = onStartCampaign,
            )

            CompanyTab.FINANCE -> FinanceTab(
                player = player,
                transactions = transactions,
                currentTick = currentTick,
                onTakeLoan = onTakeLoan,
                onRepayLoan = onRepayLoan,
            )

            CompanyTab.LEDGER -> LedgerTab(
                transactions = transactions,
                cash = player.cash,
                player = player,
                currentTick = currentTick,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Personnel
// ---------------------------------------------------------------------------------------------

@Composable
private fun PersonnelTab(
    staff: List<Staff>,
    vehicles: List<Vehicle>,
    cash: Double,
    onHireStaff: (String, StaffRole, Int) -> Unit,
    onToggleShift: (String) -> Unit,
    onAssignDriver: (String, String?) -> Unit,
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    var showHire by remember { mutableStateOf(false) }
    var assignFor by remember { mutableStateOf<Staff?>(null) }

    val filtered = when (filterIndex) {
        0 -> staff
        1 -> staff.filter { it.role == StaffRole.DRIVER }
        2 -> staff.filter { it.role == StaffRole.DISPATCHER }
        3 -> staff.filter { it.role == StaffRole.MECHANIC }
        else -> staff.filter { it.role == StaffRole.MANAGER }
    }
    val payroll = staff.sumOf { it.salaryPerDay }
    val onShift = staff.count { it.status == StaffStatus.ON_SHIFT }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Payroll",
                    value = compactMoney(payroll),
                    caption = "per 30-cycle day",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    accent = Mint,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "On shift",
                    value = "$onShift/${staff.size}",
                    caption = "${staff.count { it.fatigue >= 80 }} fatigued",
                    icon = Icons.Rounded.Bolt,
                    accent = if (staff.any { it.fatigue >= 80 }) Amber else Iris,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item("controls") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ROSTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mint,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(Mint)
                            .clickable { showHire = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = Ink, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Hire", style = MaterialTheme.typography.labelMedium, color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(9.dp))
                FilterPills(
                    items = listOf("All", "Drivers", "Dispatch", "Mechanics", "Managers"),
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                )
            }
        }

        if (filtered.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = "No people in this role",
                    message = "Recruit staff to fill the roster. Drivers are required before a vehicle can earn.",
                    icon = Icons.Rounded.PersonAdd,
                    actionLabel = "Recruitment hall",
                    onAction = { showHire = true },
                )
            }
        }

        items(filtered, key = { it.id }) { member ->
            StaffCard(
                member = member,
                vehicle = vehicles.find { it.id == member.assignedVehicleId },
                onToggleShift = { onToggleShift(member.id) },
                onAssign = if (member.role == StaffRole.DRIVER) ({ assignFor = member }) else null,
            )
        }
    }

    if (showHire) {
        HireSheet(
            cash = cash,
            onDismiss = { showHire = false },
            onHire = { name, role, skill ->
                onHireStaff(name, role, skill)
                showHire = false
            },
        )
    }

    val target = assignFor
    if (target != null) {
        VehiclePickerDialog(
            driver = target,
            vehicles = vehicles,
            onSelect = { vehicleId ->
                onAssignDriver(target.id, vehicleId)
                assignFor = null
            },
            onUnassign = {
                onAssignDriver(target.id, null)
                assignFor = null
            },
            onDismiss = { assignFor = null },
        )
    }
}

@Composable
private fun StaffCard(
    member: Staff,
    vehicle: Vehicle?,
    onToggleShift: () -> Unit,
    onAssign: (() -> Unit)?,
) {
    val fatigued = member.fatigue >= 80.0
    val statusColor = when (member.status) {
        StaffStatus.ON_SHIFT -> Mint
        StaffStatus.RESTING -> Amber
        StaffStatus.FIRED -> Coral
        StaffStatus.IDLE -> Slate
    }

    CityCard(accent = if (fatigued) Coral else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(roleColor(member.role).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.initials,
                    style = MaterialTheme.typography.titleSmall,
                    color = roleColor(member.role),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Paper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusChip(member.status.name.replace('_', ' '), statusColor)
                }
                Text(
                    "${member.role.shortLabel} · ${money(member.salaryPerDay)}/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${member.skill}/10", style = MaterialTheme.typography.labelMedium, color = Paper)
                }
                if (vehicle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(vehicle.plate, style = MaterialTheme.typography.labelSmall, color = Sky)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        MeterRow(
            label = "Fatigue",
            fraction = meterFraction(member.fatigue),
            valueLabel = "${member.fatigue.toInt()}%",
            color = if (fatigued) Coral else if (member.fatigue > 55) Amber else Mint,
        )
        Spacer(Modifier.height(8.dp))
        MeterRow(
            label = "Morale",
            fraction = meterFraction(member.morale),
            valueLabel = "${member.morale.toInt()}%",
            color = if (member.morale < 35) Coral else Iris,
        )

        if (member.status == StaffStatus.RESTING && member.restTicks > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Recovering — ${member.restTicks} cycles of rest left",
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = if (member.status == StaffStatus.ON_SHIFT) "Send on break" else "Call to shift",
                icon = if (member.status == StaffStatus.ON_SHIFT) Icons.Rounded.Bedtime else Icons.Rounded.Bolt,
                onClick = onToggleShift,
                modifier = Modifier.weight(1f),
            )
            if (onAssign != null) {
                SecondaryButton(
                    text = if (vehicle == null) "Assign vehicle" else "Swap vehicle",
                    icon = Icons.Rounded.DirectionsCar,
                    onClick = onAssign,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HireSheet(
    cash: Double,
    onDismiss: () -> Unit,
    onHire: (String, StaffRole, Int) -> Unit,
) {
    val candidates = remember {
        listOf(
            Triple("Thabo Mthembu", StaffRole.DRIVER, 7),
            Triple("Lerato Sithole", StaffRole.DRIVER, 9),
            Triple("Johan de Bruyn", StaffRole.MECHANIC, 8),
            Triple("Amina Patel", StaffRole.DISPATCHER, 8),
            Triple("Clive van Zyl", StaffRole.MANAGER, 6),
        )
    }
    val fee = 350.0

    ModalScaffold(
        overline = "Recruitment",
        title = "Hiring hall",
        subtitle = "${money(cash)} available · ${money(fee)} placement fee per hire",
        onDismiss = onDismiss,
    ) {
        items(candidates, key = { it.first }) { candidate ->
            val (name, role, skill) = candidate
            val affordable = cash >= fee
            CityCard(containerColor = SurfaceRaised, contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(roleIcon(role), roleColor(role), size = 38)
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = Paper)
                        Text(
                            "${role.shortLabel} · skill ${skill}/10 · ${money(120.0 + skill * 5)}/day",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    PrimaryButton(
                        text = "Hire",
                        onClick = { onHire(name, role, skill) },
                        enabled = affordable,
                    )
                }
            }
        }
    }
}

@Composable
private fun VehiclePickerDialog(
    driver: Staff,
    vehicles: List<Vehicle>,
    onSelect: (String) -> Unit,
    onUnassign: () -> Unit,
    onDismiss: () -> Unit,
) {
    val available = vehicles.filter { it.status == VehicleStatus.IDLE || it.id == driver.assignedVehicleId }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Text("Assign vehicle", style = MaterialTheme.typography.titleLarge, color = Paper)
                Text(driver.name, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        },
        text = {
            Column {
                if (available.isEmpty()) {
                    Text(
                        "No idle vehicles. Vehicles in transit or in the workshop cannot take a new driver.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate,
                    )
                }
                available.take(6).forEach { vehicle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(vehicle.id) }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(Icons.Rounded.LocalShipping, Mint, size = 32)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vehicle.plate, style = MaterialTheme.typography.titleSmall, color = Paper)
                            Text(
                                "${vehicle.vehicleClass.label} · ${vehicle.status.shortLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate,
                            )
                        }
                        if (vehicle.id == driver.assignedVehicleId) StatusChip("Current", Mint)
                    }
                }
            }
        },
        confirmButton = {
            SecondaryButton("Unassign", onClick = onUnassign, enabled = driver.assignedVehicleId != null)
        },
        dismissButton = { SecondaryButton("Cancel", onClick = onDismiss) },
    )
}

// ---------------------------------------------------------------------------------------------
// Territory
// ---------------------------------------------------------------------------------------------

@Composable
private fun TerritoryTab(
    zones: List<CityZone>,
    cash: Double,
    contracts: List<Contract>,
    onUnlockZone: (String) -> Unit,
) {
    var showProjection by remember { mutableStateOf(false) }
    val completed = contracts.count { it.status == ContractStatus.COMPLETED }
    val failed = contracts.count { it.status == ContractStatus.FAILED }
    val completionRate = if (completed + failed == 0) 1.0 else completed.toDouble() / (completed + failed)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Coverage",
                    value = "${zones.count { it.unlocked }}/${zones.size}",
                    caption = "zones licensed",
                    icon = Icons.Rounded.Storefront,
                    accent = Sky,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Completion",
                    value = percent(completionRate),
                    caption = "$completed delivered · $failed missed",
                    icon = Icons.Rounded.CheckCircle,
                    accent = if (completionRate >= 0.7) Mint else Amber,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item("projection_cta") {
            CityCard(accent = Violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.AutoGraph, Violet, size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Expansion projection", style = MaterialTheme.typography.titleSmall, color = Paper)
                        Text(
                            "500 Monte Carlo runs over 30 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    SecondaryButton(text = "Simulate", icon = Icons.Rounded.Tune, onClick = { showProjection = true })
                }
            }
        }

        item("zones_header") {
            SectionHeader(
                title = "City zones",
                overline = "Market",
                subtitle = "Unlock adjacent zones to lift demand",
            )
        }

        items(zones, key = { it.zoneId }) { zone ->
            ZoneCard(
                zone = zone,
                cash = cash,
                companyCompletion = completionRate,
                onUnlock = { onUnlockZone(zone.zoneId) },
            )
        }
    }

    if (showProjection) {
        ProjectionSheet(
            zones = zones,
            cash = cash,
            onDismiss = { showProjection = false },
        )
    }
}

@Composable
private fun ZoneCard(
    zone: CityZone,
    cash: Double,
    companyCompletion: Double,
    onUnlock: () -> Unit,
) {
    val affordable = cash >= zone.unlockCost
    val eligible = companyCompletion >= zone.completionRateThreshold
    val tone = if (zone.unlocked) Mint else if (zone.unlockCost == 0.0) Sky else Muted
    val zoneDelivered = zone.completedContracts
    val zoneFailed = zone.failedContracts
    val zoneClosure = if (zoneDelivered + zoneFailed == 0) 1.0
    else zoneDelivered.toDouble() / (zoneDelivered + zoneFailed)

    CityCard(accent = if (zone.unlocked) Mint.copy(alpha = 0.5f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = if (zone.unlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                tint = tone,
                size = 40,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(zone.name, style = MaterialTheme.typography.titleSmall, color = Paper)
                Text(
                    "${zone.demandMultiplier}x demand · ${percent(zone.trafficIndex)} congestion",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
            StatusChip(if (zone.unlocked) "Licensed" else "Locked", tone)
        }

        Spacer(Modifier.height(12.dp))
        MeterRow(
            label = "Zone delivery rate",
            fraction = meterFraction(zoneClosure * 100),
            valueLabel = percent(zoneClosure),
            color = if (zoneClosure >= 0.7) Mint else Amber,
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            ZoneStat("Delivered", "${zone.completedContracts}", Mint)
            ZoneStat("Failed", "${zone.failedContracts}", if (zone.failedContracts > 0) Coral else Slate)
            ZoneStat("Unmet", "${zone.unmetDemand}", Slate)
            ZoneStat("Traffic", percent(zone.trafficIndex), Amber)
        }

        if (!zone.unlocked) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Licence fee", style = MaterialTheme.typography.labelSmall, color = Slate)
                    Text(
                        money(zone.unlockCost),
                        style = NumericStyle.copy(fontSize = 16.sp),
                        color = if (affordable) Mint else Coral,
                    )
                }
                PrimaryButton(
                    text = if (eligible) "Unlock zone" else "Need ${percent(zone.completionRateThreshold)}",
                    onClick = onUnlock,
                    enabled = affordable && eligible,
                )
            }
            if (!affordable) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Short by ${money(zone.unlockCost - cash)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Coral,
                )
            }
            if (!eligible) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Licence requires ${percent(zone.completionRateThreshold)} company completion · currently ${percent(companyCompletion)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                )
            }
        }
    }
}

@Composable
private fun ZoneStat(label: String, value: String, color: Color) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted, fontSize = 9.sp)
        Text(value, style = NumericStyle, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectionSheet(zones: List<CityZone>, cash: Double, onDismiss: () -> Unit) {
    val candidate = zones.firstOrNull { !it.unlocked } ?: zones.firstOrNull()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var vans by remember { mutableIntStateOf(2) }
    var drivers by remember { mutableIntStateOf(2) }
    var marketing by remember { mutableFloatStateOf(2500f) }
    var result by remember { mutableStateOf<MonteCarloOutput?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("title") {
                Column {
                    Text("PROJECTION", style = MaterialTheme.typography.labelSmall, color = Violet)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        candidate?.let { "Expand into ${it.name}" } ?: "Expansion projection",
                        style = MaterialTheme.typography.titleLarge,
                        color = Paper,
                    )
                    Text(
                        "Seeded 30-day cash-flow distribution. Read-only — nothing is purchased.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                }
            }

            item("sliders") {
                CityCard {
                    SliderField(
                        label = "Additional vans",
                        value = "$vans (${money(vans * 14000.0)})",
                        slider = {
                            Slider(
                                value = vans.toFloat(),
                                onValueChange = { vans = it.toInt() },
                                valueRange = 0f..6f,
                                steps = 5,
                                colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint),
                            )
                        },
                    )
                    SliderField(
                        label = "Driver hires",
                        value = "$drivers (${money(drivers * 350.0)})",
                        slider = {
                            Slider(
                                value = drivers.toFloat(),
                                onValueChange = { drivers = it.toInt() },
                                valueRange = 0f..6f,
                                steps = 5,
                                colors = SliderDefaults.colors(thumbColor = Iris, activeTrackColor = Iris),
                            )
                        },
                    )
                    SliderField(
                        label = "Marketing",
                        value = money(marketing.toDouble()),
                        slider = {
                            Slider(
                                value = marketing,
                                onValueChange = { marketing = it },
                                valueRange = 0f..10000f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                            )
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    PrimaryButton(
                        text = "Run simulation",
                        onClick = {
                            val zone = candidate ?: return@PrimaryButton
                            result = MonteCarloSimulator.runSimulation(
                                MonteCarloInput(
                                    currentCash = cash,
                                    targetZone = zone,
                                    additionalVehicles = mapOf(VehicleClass.VAN to vans),
                                    additionalDrivers = drivers,
                                    marketingSpend = marketing.toDouble(),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val projection = result
            if (projection != null) {
                item("results") {
                    ProjectionResult(projection)
                }
            }
        }
    }
}

@Composable
private fun SliderField(label: String, value: String, slider: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slate)
        Text(value, style = NumericStyle, color = Paper)
    }
    slider()
}

@Composable
private fun ProjectionResult(result: MonteCarloOutput) {
    CityCard(accent = Violet) {
        Text("30-day cash at risk", style = MaterialTheme.typography.labelSmall, color = Slate)
        Spacer(Modifier.height(4.dp))
        Text(
            "${compactMoney(result.p10FinalCash)} – ${compactMoney(result.p90FinalCash)}",
            style = NumericStyle.copy(fontSize = 20.sp),
            color = Paper,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "P50 ${compactMoney(result.p50FinalCash)} · ${MonteCarloSimulator.NUM_ITERATIONS} iterations",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.copy(alpha = 0.5f))
                .padding(10.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxValue = result.dailyProjections.maxOfOrNull { it.p90Cash }?.coerceAtLeast(1.0) ?: 1.0
                val minimum = result.dailyProjections.minOfOrNull { it.p10Cash } ?: 0.0
                val span = (maxValue - minimum).coerceAtLeast(1.0)
                fun y(value: Double) = (size.height - ((value - minimum) / span * size.height)).toFloat()
                fun x(day: Int) = day.toFloat() / 30f * size.width

                fun line(selector: (Int) -> Double, color: Color) {
                    val path = Path()
                    runCatching {
                        path.moveTo(x(0), y(selector(0)))
                        for (day in 1..30) path.lineTo(x(day), y(selector(day)))
                        drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    }
                }
                line({ result.dailyProjections[it].p10Cash }, Amber)
                line({ result.dailyProjections[it].p90Cash }, Sky)
                line({ result.dailyProjections[it].p50Cash }, Mint)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ZoneStat("Break-even", result.breakEvenDay?.let { "Day $it" } ?: "n/a", Mint)
            ZoneStat("Insolvency", percent(result.probabilityOfInsolvency), if (result.probabilityOfInsolvency > 0.15) Coral else Mint)
            ZoneStat("Net ROI", percent(result.projectedNetRoi, 1), if (result.projectedNetRoi >= 0) Mint else Coral)
            ZoneStat("Horizon", "${MonteCarloSimulator.SIMULATION_DAYS}d", Paper)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ledger
// ---------------------------------------------------------------------------------------------

@Composable
private fun LedgerTab(
    transactions: List<Transaction>,
    cash: Double,
    player: Player,
    currentTick: Long,
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Income", "Fuel", "Payroll", "Fleet", "Other")

    val filtered = when (filterIndex) {
        0 -> transactions
        1 -> transactions.filter { it.category.isIncome }
        2 -> transactions.filter { it.category == TransactionCategory.FUEL }
        3 -> transactions.filter { it.category == TransactionCategory.SALARY }
        4 -> transactions.filter {
            it.category in setOf(
            TransactionCategory.PURCHASE, TransactionCategory.REPAIR, TransactionCategory.TOW,
            TransactionCategory.EXPANSION, TransactionCategory.MARKETING,
        )
        }
        else -> transactions.filter {
            it.category !in setOf(
                TransactionCategory.REVENUE, TransactionCategory.LOAN, TransactionCategory.FUEL,
                TransactionCategory.SALARY, TransactionCategory.PURCHASE, TransactionCategory.REPAIR,
                TransactionCategory.TOW, TransactionCategory.EXPANSION, TransactionCategory.MARKETING,
            )
        }
    }
    val income = transactions.filter { it.category.isIncome }.sumOf { it.amount }
    val spend = transactions.filter { !it.category.isIncome }.sumOf { -it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("balance") {
            CityCard(accent = Mint) {
                Text("CASH ON HAND", style = MaterialTheme.typography.labelSmall, color = Slate)
                Spacer(Modifier.height(4.dp))
                Text(money(cash), style = MaterialTheme.typography.displaySmall, color = Paper)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LedgerTotal(Icons.Rounded.ArrowDownward, "Income", money(income), Mint)
                    LedgerTotal(Icons.Rounded.ArrowUpward, "Spend", money(spend), Amber)
                }
            }
        }

        item("career") {
            CityCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.TrendingUp, Iris, size = 38)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Operator record", style = MaterialTheme.typography.titleSmall, color = Paper)
                        Text(
                            "${player.contractsCompleted} delivered · ${player.contractsFailed} missed · ${player.distanceKm.toInt()} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    StatusChip("LV ${player.level}", Iris)
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Lifetime revenue", money(player.totalRevenue), mono = true)
                Spacer(Modifier.height(6.dp))
                KeyValueRow("Reputation", "${player.reputation.toInt()}/100", valueColor = Mint)
            }
        }

        item("filters") {
            FilterPills(items = filters, selectedIndex = filterIndex, onSelect = { filterIndex = it })
        }

        if (filtered.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = "No entries",
                    message = "This filter has no ledger rows yet. Deliver work or spend on the fleet to fill the books.",
                    icon = Icons.Rounded.Receipt,
                )
            }
        }

        var lastDay = -1
        filtered.forEach { tx ->
            val clock = simClock(tx.tick)
            if (clock.day != lastDay) {
                lastDay = clock.day
                item("day-${clock.day}-${tx.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "DAY ${clock.day}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Hairline)
                        )
                    }
                }
            }
            item("tx-${tx.id}") {
                LedgerRow(tx = tx, currentTick = currentTick)
            }
        }
    }
}

@Composable
private fun LedgerTotal(icon: ImageVector, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(value, style = NumericStyle, color = color)
        }
    }
}

@Composable
private fun LedgerRow(tx: Transaction, currentTick: Long) {
    val color = categoryColor(tx.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(categoryIcon(tx.category), color, size = 34)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.description.ifBlank { tx.category.label },
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${tx.category.label} · ${simClock(tx.tick).time}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            signedMoney(tx.amount),
            style = NumericStyle,
            color = if (tx.amount >= 0) Mint else Paper,
            textAlign = TextAlign.End,
        )
    }
}

private fun categoryColor(category: TransactionCategory): Color = when (category) {
    TransactionCategory.REVENUE -> Mint
    TransactionCategory.LOAN -> Sky
    TransactionCategory.FUEL -> Amber
    TransactionCategory.SALARY -> Iris
    TransactionCategory.REPAIR -> Violet
    TransactionCategory.TOW -> Coral
    TransactionCategory.EXPANSION -> Sky
    TransactionCategory.PURCHASE -> Violet
    TransactionCategory.PENALTY -> Coral
    TransactionCategory.MARKETING -> Amber
}

private fun categoryIcon(category: TransactionCategory): ImageVector = when (category) {
    TransactionCategory.REVENUE -> Icons.Rounded.ArrowDownward
    TransactionCategory.LOAN -> Icons.Rounded.AccountBalanceWallet
    TransactionCategory.FUEL -> Icons.Rounded.LocalGasStation
    TransactionCategory.SALARY -> Icons.Rounded.ManageAccounts
    TransactionCategory.REPAIR -> Icons.Rounded.Build
    TransactionCategory.TOW -> Icons.Rounded.WarningAmber
    TransactionCategory.EXPANSION -> Icons.Rounded.Storefront
    TransactionCategory.PURCHASE -> Icons.Rounded.LocalShipping
    TransactionCategory.PENALTY -> Icons.Rounded.WarningAmber
    TransactionCategory.MARKETING -> Icons.Rounded.Storefront
}

// ---------------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------------

@Composable
private fun ModalScaffold(
    overline: String,
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    ModalBottomSheetScaffold(
        overline = overline,
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalBottomSheetScaffold(
    overline: String,
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("modal_header") {
                Column {
                    Text(overline.uppercase(), style = MaterialTheme.typography.labelSmall, color = Mint)
                    Spacer(Modifier.height(2.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Paper)
                    if (subtitle != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate)
                    }
                }
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Marketing
// ---------------------------------------------------------------------------------------------

@Composable
private fun MarketingTab(
    campaigns: List<Campaign>,
    zones: List<CityZone>,
    player: Player,
    onStartCampaign: (String, Double) -> Unit,
) {
    val active = campaigns.filter { it.isActive }
    val invested = campaigns.sumOf { it.spend }
    val licensed = zones.filter { it.unlocked }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("marketing_summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Live campaigns",
                    value = "${active.size}",
                    caption = "${licensed.size} zones licensed",
                    icon = Icons.Rounded.Storefront,
                    accent = Iris,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Invested",
                    value = compactMoney(invested),
                    caption = "Career marketing spend",
                    icon = Icons.Rounded.AutoGraph,
                    accent = Mint,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        player.cityEvent?.let { event ->
            item("marketing_event") {
                CityCard(accent = Amber) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.WarningAmber, Amber, size = 36)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CITY EVENT", style = MaterialTheme.typography.labelSmall, color = Amber)
                            Text(event.label, style = MaterialTheme.typography.titleSmall, color = Paper)
                            Text(event.effectLabel, style = MaterialTheme.typography.bodySmall, color = Slate)
                        }
                        Text("${event.ticksLeft}c", style = NumericStyle, color = Amber)
                    }
                }
            }
        }

        item("marketing_header") {
            SectionHeader(
                title = "Demand generation",
                overline = "Marketing",
                subtitle = "${money(3000.0)} buys 60 cycles of higher demand and payouts in one zone.",
            )
        }

        if (licensed.isEmpty()) {
            item("marketing_empty") {
                EmptyState(
                    title = "No licensed zones",
                    message = "Unlock a territory before advertising in it.",
                    icon = Icons.Rounded.Storefront,
                )
            }
        }

        items(licensed, key = { "mkt-${it.zoneId}" }) { zone ->
            val campaign = campaigns.firstOrNull { it.zoneId == zone.zoneId && it.isActive }
            val finished = campaign == null && campaigns.any { it.zoneId == zone.zoneId && !it.isActive }
            CityCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(icon = Icons.Rounded.Storefront, tint = Iris, size = 38)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(zone.name, style = MaterialTheme.typography.titleSmall, color = Paper)
                        Text(
                            "Demand ×${String.format(java.util.Locale.US, "%.2f", zone.demandMultiplier)} · ${(zone.trafficIndex * 100).toInt()}% traffic",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    when {
                        campaign != null -> StatusChip("${campaign.ticksLeft}c left", Mint)
                        finished -> StatusChip("Ended", Slate)
                        else -> StatusChip("Idle", Slate)
                    }
                }

                if (campaign != null) {
                    Spacer(Modifier.height(10.dp))
                    MeterRow(
                        label = "Campaign window",
                        fraction = (campaign.ticksLeft / 60f).coerceIn(0f, 1f),
                        valueLabel = "${campaign.ticksLeft}c",
                        color = Mint,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "+${(campaign.demandBoost * 100).toInt()}% demand · +${(campaign.rewardBoost * 100).toInt()}% payouts · ${money(campaign.spend)} invested",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mint,
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "+100% demand · +50% payouts for 60 cycles",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Launch ${money(3000.0)}",
                            onClick = { onStartCampaign(zone.zoneId, 3000.0) },
                            enabled = player.cash >= 3000.0,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Finance
// ---------------------------------------------------------------------------------------------

@Composable
private fun FinanceTab(
    player: Player,
    transactions: List<Transaction>,
    currentTick: Long,
    onTakeLoan: (Double) -> Unit,
    onRepayLoan: (Double) -> Unit,
) {
    val loan = player.loan
    val window = transactions.filter { it.tick >= currentTick - 30 }
    val revenue = window.filter { it.category == TransactionCategory.REVENUE }.sumOf { it.amount }
    val spend = window.filter { !it.category.isIncome }.sumOf { -it.amount }
    val interest = window.filter { it.category == TransactionCategory.LOAN && it.amount < 0 }.sumOf { -it.amount }
    val marketingSpend = window.filter { it.category == TransactionCategory.MARKETING }.sumOf { -it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("finance_summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Cash",
                    value = compactMoney(player.cash),
                    caption = "Credit line ${compactMoney(player.creditLimit)}",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    accent = Mint,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Net 30 cycles",
                    value = signedMoney(revenue - spend),
                    caption = "${money(revenue)} in",
                    icon = Icons.Rounded.TrendingUp,
                    accent = if (revenue >= spend) Mint else Amber,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item("loan") {
            CityCard(accent = if (loan != null) Amber else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(icon = Icons.Rounded.AccountBalance, tint = if (loan != null) Amber else Iris, size = 38)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OPERATING CREDIT", style = MaterialTheme.typography.labelSmall, color = Slate)
                        Text(
                            if (loan == null) "No money borrowed" else "${money(loan.remaining)} outstanding",
                            style = MaterialTheme.typography.titleSmall,
                            color = Paper,
                        )
                    }
                    StatusChip(if (loan == null) "Available" else "Active", if (loan == null) Mint else Amber)
                }
                Spacer(Modifier.height(10.dp))
                if (loan == null) {
                    KeyValueRow("Credit limit", money(player.creditLimit), mono = true)
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("Rate", "6% per payroll", valueColor = Amber)
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("Term", "240 cycles")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The line grows with every delivery. Interest is charged each payroll and principal repays automatically while you hold a cash buffer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Draw ${money(minOf(5000.0, player.creditLimit))}",
                        onClick = { onTakeLoan(minOf(5000.0, player.creditLimit)) },
                        enabled = player.creditLimit >= 500.0,
                    )
                } else {
                    KeyValueRow("Outstanding", money(loan.remaining), valueColor = Amber, mono = true)
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("Interest next payroll", money(loan.interestPerPayroll), valueColor = Amber, mono = true)
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("Term ends", deadlineLabel(loan.dueTick, currentTick))
                    Spacer(Modifier.height(10.dp))
                    MeterRow(
                        label = "Repaid",
                        fraction = loan.repaidFraction,
                        valueLabel = "${(loan.repaidFraction * 100).toInt()}%",
                        color = Mint,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Repay ${money(loan.remaining)}",
                        onClick = { onRepayLoan(loan.remaining) },
                        enabled = player.cash >= loan.remaining,
                    )
                }
            }
        }

        item("cashflow") {
            CityCard {
                Text("LAST 30 CYCLES", style = MaterialTheme.typography.labelSmall, color = Slate)
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Contract revenue", money(revenue), valueColor = Mint, mono = true)
                Spacer(Modifier.height(6.dp))
                KeyValueRow("Operating spend", money(spend), valueColor = Amber, mono = true)
                Spacer(Modifier.height(6.dp))
                KeyValueRow("Marketing", money(marketingSpend), mono = true)
                Spacer(Modifier.height(6.dp))
                KeyValueRow("Loan interest", money(interest), valueColor = if (interest > 0) Amber else Paper, mono = true)
            }
        }

        item("finance_note") {
            CityCard(containerColor = SurfaceRaised.copy(alpha = 0.7f)) {
                Text(
                    "Borrowing is cheapest when the board is full: interest accrues every payroll, and a shortfall is what triggers a strike.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }
    }
}

private fun roleColor(role: StaffRole): Color = when (role) {
    StaffRole.DRIVER -> Mint
    StaffRole.DISPATCHER -> Sky
    StaffRole.MECHANIC -> Amber
    StaffRole.MANAGER -> Iris
}

private fun roleIcon(role: StaffRole): ImageVector = when (role) {
    StaffRole.DRIVER -> Icons.Rounded.DirectionsCar
    StaffRole.DISPATCHER -> Icons.Rounded.HeadsetMic
    StaffRole.MECHANIC -> Icons.Rounded.Build
    StaffRole.MANAGER -> Icons.Rounded.ManageAccounts
}
