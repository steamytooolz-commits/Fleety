package com.example.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CityBorderNavy
import com.example.ui.theme.CityCardNavy
import com.example.ui.theme.CitySurfaceNavy
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun KpiStrip(
    cash: Double,
    todayRevenue: Double,
    activeContractsCount: Int,
    idleVehiclesCount: Int,
    totalVehiclesCount: Int,
    staffOnShiftCount: Int,
    currentTick: Long,
    isPaused: Boolean,
    speedMultiplier: Float,
    onTogglePause: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onStepTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1 tick = 1 min. 30 ticks = 1 simulated day.
    val simDay = (currentTick / 30L) + 1
    val minuteOfDay = (currentTick % 30L) * 48 // scale to 24h day
    val hour = (6 + (minuteOfDay / 60)) % 24
    val minute = minuteOfDay % 60
    val timeFormatted = String.format("%02d:%02d", hour, minute)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("kpi_strip_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
        border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Clock & Simulation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Day $simDay • $timeFormatted",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(Tick #$currentTick)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedDark
                    )
                }

                // Speed buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pause / Resume
                    val playPauseColor by animateColorAsState(if (isPaused) AmberWarning else CyanNeon)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPaused) AmberWarning.copy(alpha = 0.2f) else CityCardNavy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier.testTag("sim_pause_resume_btn")
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                tint = playPauseColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 1x, 2x, 5x pills
                    listOf(1f, 2f, 5f).forEach { speed ->
                        val isSelected = speedMultiplier == speed && !isPaused
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyanNeon else CityCardNavy,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("sim_speed_${speed.toInt()}x_btn"),
                            onClick = { onSetSpeed(speed) }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${speed.toInt()}x",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CitySurfaceNavy else TextSecondaryDark
                                )
                            }
                        }
                    }

                    // Step button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CityCardNavy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = onStepTick,
                            modifier = Modifier.testTag("sim_step_btn")
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Step Tick",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontally Scrollable KPI metrics strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricItem(
                    label = "Cash Balance",
                    value = "$${String.format("%,.0f", cash)}",
                    icon = Icons.Default.AttachMoney,
                    color = if (cash > 2000.0) EmeraldSuccess else AmberWarning
                )
                KpiMetricItem(
                    label = "Today's Revenue",
                    value = "+$${String.format("%,.0f", todayRevenue)}",
                    icon = Icons.Default.TrendingUp,
                    color = CyanNeon
                )
                KpiMetricItem(
                    label = "Active Contracts",
                    value = "$activeContractsCount",
                    icon = Icons.Default.FastForward,
                    color = EmeraldSuccess
                )
                KpiMetricItem(
                    label = "Fleet Availability",
                    value = "$idleVehiclesCount / $totalVehiclesCount idle",
                    icon = Icons.Default.Schedule,
                    color = TextPrimaryDark
                )
                KpiMetricItem(
                    label = "Staff on Duty",
                    value = "$staffOnShiftCount on shift",
                    icon = Icons.Default.People,
                    color = TextPrimaryDark
                )
            }
        }
    }
}

@Composable
private fun KpiMetricItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CityCardNavy,
        border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = label, fontSize = 10.sp, color = TextSecondaryDark)
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
            }
        }
    }
}
