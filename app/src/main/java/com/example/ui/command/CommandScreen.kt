package com.example.ui.command

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectionState
import com.example.model.Contract
import com.example.model.ContractStatus
import com.example.model.Incident
import com.example.model.Player
import com.example.model.Staff
import com.example.model.Transaction
import com.example.model.Vehicle
import com.example.model.VehicleStatus
import com.example.model.shortLabel
import com.example.ui.components.CityCard
import com.example.ui.components.CountBadge
import com.example.ui.components.Dot
import com.example.ui.components.IconTile
import com.example.ui.components.MeterRow
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.components.StatusChip
import com.example.ui.components.compactMoney
import com.example.ui.components.deadlineLabel
import com.example.ui.components.isLate
import com.example.ui.components.km
import com.example.ui.components.money
import com.example.ui.components.plural
import com.example.ui.components.signedMoney
import com.example.ui.components.simClock
import com.example.ui.components.ticksLeft
import com.example.ui.theme.Amber
import com.example.ui.theme.Coral
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.Iris
import com.example.ui.theme.Mint
import com.example.ui.theme.Muted
import com.example.ui.theme.NumericDisplay
import com.example.ui.theme.NumericLarge
import com.example.ui.theme.NumericStyle
import com.example.ui.theme.Paper
import com.example.ui.theme.PillShape
import com.example.ui.theme.Sky
import com.example.ui.theme.Slate
import com.example.ui.theme.SurfaceHigh
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.Typography as CityTypography

/**
 * Command desk: the operator's morning brief. Clock controls, the four numbers that decide the
 * shift, anything on fire, work waiting on the board and the money that moved most recently.
 */
@Composable
fun CommandScreen(
    player: Player,
    vehicles: List<Vehicle>,
    contracts: List<Contract>,
    staff: Map<String, Staff>,
    incidents: List<Incident>,
    transactions: List<Transaction>,
    currentTick: Long,
    isPaused: Boolean,
    speedMultiplier: Float,
    autoDispatchEnabled: Boolean,
    connection: ConnectionState,
    onTogglePause: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onStepTick: () -> Unit,
    onOpenIncident: (Incident) -> Unit,
    onOpenDispatch: () -> Unit,
    onOpenFleet: () -> Unit,
    onOpenCompany: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openContracts = contracts.filter { it.status == ContractStatus.OPEN }
    val activeContracts = contracts.filter { it.isActive }
    val idleVehicles = vehicles.filter { it.status == VehicleStatus.IDLE }
    val workingVehicles = vehicles.filter { it.isWorking }
    val atRisk = vehicles.count { it.fuel < 20.0 || it.condition < 35.0 || it.status == VehicleStatus.BREAKDOWN }
    val activeIncidents = incidents.filter { !it.resolved }
    val dayStartTick = (currentTick / 30L) * 30L
    val todayRevenue = transactions
        .filter { it.category.isIncome && it.tick >= dayStartTick }
        .sumOf { it.amount }
    val todaySpend = transactions
        .filter { !it.category.isIncome && it.tick >= dayStartTick }
        .sumOf { -it.amount }
    val clock = simClock(currentTick)
    val driversOnShift = staff.values.count { it.status == com.example.model.StaffStatus.ON_SHIFT }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("command_screen_root"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("greeting") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OPERATIONS DESK",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mint,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = player.companyName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Paper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                LevelBadge(level = player.level, progress = player.levelProgress, reputation = player.reputation)
            }
        }

        item("clock") {
            ClockCard(
                clockLabel = clock.label,
                cycle = (currentTick % 30L).toInt(),
                isPaused = isPaused,
                speed = speedMultiplier,
                connection = connection,
                onTogglePause = onTogglePause,
                onSetSpeed = onSetSpeed,
                onStepTick = onStepTick,
            )
        }

        item("kpis") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Cash",
                    value = compactMoney(player.cash),
                    caption = "Net today ${signedMoney(todayRevenue - todaySpend)}",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    accent = if (todayRevenue >= todaySpend) Mint else Amber,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Revenue today",
                    value = compactMoney(todayRevenue),
                    caption = "${activeContracts.size.plural("job")} in transit",
                    icon = Icons.Rounded.TrendingUp,
                    accent = Mint,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item("kpis2") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Open board",
                    value = "${openContracts.size}",
                    caption = if (autoDispatchEnabled) "Auto-dispatch on" else "Manual dispatch",
                    icon = Icons.Rounded.Bolt,
                    accent = Sky,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Fleet ready",
                    value = "${idleVehicles.size}/${vehicles.size}",
                    caption = if (atRisk > 0) "$atRisk need attention" else "All assets healthy",
                    icon = Icons.Rounded.LocalShipping,
                    accent = if (atRisk > 0) Coral else Iris,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (activeIncidents.isNotEmpty()) {
            item("alerts_header") {
                SectionHeader(
                    title = "Needs attention",
                    subtitle = "${activeIncidents.size} open issue${if (activeIncidents.size == 1) "" else "s"}",
                    overline = "Critical",
                )
            }
            items(activeIncidents.take(3), key = { "inc-${it.id}" }) { incident ->
                IncidentRow(incident = incident, onClick = { onOpenIncident(incident) })
            }
        } else {
            item("alerts_clear") {
                CityCard(containerColor = SurfaceRaised.copy(alpha = 0.7f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon = Icons.Rounded.WarningAmber, tint = Mint, size = 36)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("All clear", style = MaterialTheme.typography.titleSmall, color = Paper)
                            Text(
                                "No open incidents. The fleet is running on schedule.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate,
                            )
                        }
                    }
                }
            }
        }

        item("board_header") {
            SectionHeader(
                title = "Dispatch board",
                overline = "Work queue",
                subtitle = "${openContracts.size} unassigned · ${activeContracts.size} on the road",
                actionLabel = "View all",
                onAction = onOpenDispatch,
            )
        }

        if (openContracts.isEmpty()) {
            item("board_empty") {
                CityCard {
                    Text(
                        "The board is clear. New demand arrives every dispatch cycle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate,
                    )
                }
            }
        } else {
            items(openContracts.take(3), key = { "ctr-${it.id}" }) { contract ->
                CompactContractRow(contract = contract, currentTick = currentTick, onClick = onOpenDispatch)
            }
        }

        item("fleet_header") {
            SectionHeader(
                title = "Fleet posture",
                overline = "Assets",
                subtitle = "${workingVehicles.size} working · ${idleVehicles.size} idle · $driversOnShift staff on shift",
                actionLabel = "Fleet",
                onAction = onOpenFleet,
            )
        }

        item("fleet_posture") {
            CityCard {
                MeterRow(
                    label = "Fleet utilisation",
                    fraction = if (vehicles.isEmpty()) 0f else workingVehicles.size.toFloat() / vehicles.size,
                    valueLabel = "${workingVehicles.size}/${vehicles.size}",
                    color = Mint,
                )
                Spacer(Modifier.height(12.dp))
                MeterRow(
                    label = "Fuel health",
                    fraction = if (vehicles.isEmpty()) 0f else vehicles.map { it.fuel }.average().toFloat() / 100f,
                    valueLabel = if (vehicles.isEmpty()) "—" else "${vehicles.map { it.fuel }.average().toInt()}%",
                    color = if (vehicles.any { it.fuel < 20 }) Amber else Iris,
                )
                Spacer(Modifier.height(12.dp))
                MeterRow(
                    label = "Condition",
                    fraction = if (vehicles.isEmpty()) 0f else vehicles.map { it.condition }.average().toFloat() / 100f,
                    valueLabel = if (vehicles.isEmpty()) "—" else "${vehicles.map { it.condition }.average().toInt()}%",
                    color = Coral,
                )
            }
        }

        item("ledger_header") {
            SectionHeader(
                title = "Latest ledger",
                overline = "Money",
                subtitle = "Spend today ${money(todaySpend)}",
                actionLabel = "Finance",
                onAction = onOpenCompany,
            )
        }

        if (transactions.isEmpty()) {
            item("ledger_empty") {
                CityCard {
                    Text(
                        "No transactions yet. Deliver a contract to open the books.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate,
                    )
                }
            }
        } else {
            items(transactions.take(4), key = { "tx-${it.id}" }) { tx ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(color = if (tx.category.isIncome) Mint else Amber, size = 7)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tx.description.ifBlank { tx.category.label },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(tx.category.label, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    Text(
                        text = signedMoney(tx.amount),
                        style = NumericStyle,
                        color = if (tx.amount >= 0) Mint else Paper,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelBadge(level: Int, progress: Float, reputation: Double) {
    Column(horizontalAlignment = Alignment.End) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(Iris.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(color = Iris, size = 6)
            Spacer(Modifier.width(6.dp))
            Text("LV $level", style = MaterialTheme.typography.labelMedium, color = Iris, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(4.dp)
                .clip(PillShape)
                .background(SurfaceHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(PillShape)
                    .background(Iris),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "REP ${reputation.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

@Composable
private fun ClockCard(
    clockLabel: String,
    cycle: Int,
    isPaused: Boolean,
    speed: Float,
    connection: ConnectionState,
    onTogglePause: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onStepTick: () -> Unit,
) {
    CityCard(accent = if (isPaused) Amber else Mint) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SIMULATION CLOCK", style = MaterialTheme.typography.labelSmall, color = Slate)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(
                    color = when (connection) {
                        ConnectionState.ONLINE -> Mint
                        ConnectionState.CONNECTING -> Amber
                        ConnectionState.OFFLINE -> Coral
                    },
                    size = 6,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = when (connection) {
                        ConnectionState.ONLINE -> if (isPaused) "HOLD" else "LIVE"
                        ConnectionState.CONNECTING -> "LINKING"
                        ConnectionState.OFFLINE -> "OFFLINE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (connection) {
                        ConnectionState.ONLINE -> if (isPaused) Amber else Mint
                        ConnectionState.CONNECTING -> Amber
                        ConnectionState.OFFLINE -> Coral
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(clockLabel, style = NumericDisplay.copy(fontSize = 30.sp), color = Paper)
        Spacer(Modifier.height(2.dp))
        Text(
            "Dispatch cycle $cycle of 30 · payroll every 30 cycles",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isPaused) Amber else Mint)
                    .clickable(onClick = onTogglePause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (isPaused) "Resume simulation" else "Pause simulation",
                    tint = Ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(Ink.copy(alpha = 0.45f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(1f, 2f, 4f).forEach { option ->
                    val selected = !isPaused && speed == option
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(if (selected) Mint else Color.Transparent)
                            .clickable { onSetSpeed(option) }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "${option.toInt()}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Ink else Slate,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(0.dp))
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceHigh)
                    .clickable(onClick = onStepTick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Advance one dispatch cycle",
                    tint = Paper,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun IncidentRow(incident: Incident, onClick: () -> Unit) {
    val color = if (incident.severity >= 3) Coral else Amber
    CityCard(accent = color, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.Rounded.WarningAmber, tint = color, size = 38)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    incident.type.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    incident.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(if (incident.severity >= 3) "Critical" else "Warning", color)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (incident.severity >= 3) "Resolve" else "Review",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun CompactContractRow(contract: Contract, currentTick: Long, onClick: () -> Unit) {
    val late = isLate(contract.deadlineTick, currentTick)
    CityCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(contract.type.baseColorHex).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color(contract.type.baseColorHex),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${contract.pickup.label} → ${contract.dropoff.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${contract.type.label} · ${km(contract.estimatedKm)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(money(contract.reward), style = NumericStyle, color = Mint)
                Text(
                    deadlineLabel(contract.deadlineTick, currentTick),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (late) Coral else Slate,
                )
            }
        }
    }
}
