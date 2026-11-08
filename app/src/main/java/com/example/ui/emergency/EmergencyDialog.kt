package com.example.ui.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Incident
import com.example.model.IncidentType
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.StatusChip
import com.example.ui.theme.Amber
import com.example.ui.theme.Coral
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.Mint
import com.example.ui.theme.NumericStyle
import com.example.ui.theme.Paper
import com.example.ui.theme.Slate
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceHigh

/**
 * Blocking operator interrupt. Severity-3 incidents arrive with the simulation clock already
 * paused; closing this sheet resumes it (handled by the view model).
 */
@Composable
fun EmergencyDialog(
    incident: Incident,
    onResolveLowFuel: (action: String) -> Unit,
    onResolveBreakdown: (action: String) -> Unit,
    onResolveStrike: (action: String) -> Unit,
    onDismiss: () -> Unit
) {
    val urgent = incident.severity >= 3
    val accent = if (urgent) Coral else Amber

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.padding(20.dp),
            shape = RoundedCornerShape(26.dp),
            color = SurfaceBase,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(incident.type.label, style = MaterialTheme.typography.titleMedium, color = Paper)
                        Text(
                            if (urgent) "SEVERITY 3 · CLOCK HELD" else "OPERATIONAL ALERT",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                        )
                    }
                    StatusChip(if (urgent) "Critical" else "Warning", accent)
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    incident.description.ifBlank { incident.type.label },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate,
                )
                if (incident.costIfIgnored > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceHigh.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("If ignored", style = MaterialTheme.typography.bodySmall, color = Slate)
                        Text(
                            "$${incident.costIfIgnored.toInt()} exposure",
                            style = NumericStyle,
                            color = Amber,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("RESOLUTION", style = MaterialTheme.typography.labelSmall, color = Mint)
                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (incident.type) {
                        IncidentType.LOW_FUEL -> {
                            PrimaryButton(
                                text = "Send fuel truck · $120",
                                icon = Icons.Rounded.LocalGasStation,
                                onClick = {
                                    onResolveLowFuel("DISPATCH_FUEL_TRUCK")
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resolve_fuel_truck_btn"),
                            )
                            SecondaryButton(
                                text = "Reroute to nearest depot",
                                onClick = {
                                    onResolveLowFuel("REROUTE_STATION")
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resolve_reroute_fuel_btn"),
                            )
                        }

                        IncidentType.BREAKDOWN, IncidentType.ACCIDENT -> {
                            PrimaryButton(
                                text = "Tow to base bay · $350",
                                onClick = {
                                    onResolveBreakdown("TOW_TO_BASE")
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resolve_tow_base_btn"),
                            )
                            SecondaryButton(
                                text = "Field repair · $520",
                                onClick = {
                                    onResolveBreakdown("FIELD_REPAIR")
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resolve_field_repair_btn"),
                            )
                        }

                        IncidentType.STRIKE -> {
                            PrimaryButton(
                                text = "Bridge loan · +$5,000",
                                onClick = {
                                    onResolveStrike("TAKE_LOAN")
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resolve_take_loan_btn"),
                            )
                            SecondaryButton(
                                text = "Settle half of the driver payroll",
                                onClick = {
                                    onResolveStrike("PAY_BONUS")
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resolve_pay_bonus_btn"),
                            )
                        }

                        else -> {
                            PrimaryButton(
                                text = "Acknowledge",
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "Postpone",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (urgent) Coral else Slate,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("emergency_postpone_btn")
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
