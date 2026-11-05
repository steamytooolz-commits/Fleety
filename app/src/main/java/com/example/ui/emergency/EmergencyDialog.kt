package com.example.ui.emergency

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Incident
import com.example.model.IncidentType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CityCardNavy
import com.example.ui.theme.CitySurfaceNavy
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun EmergencyDialog(
    incident: Incident,
    onResolveLowFuel: (action: String) -> Unit,
    onResolveBreakdown: (action: String) -> Unit,
    onResolveStrike: (action: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isUrgent = incident.severity >= 3
    val alertColor = if (isUrgent) CrimsonError else AmberWarning

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CitySurfaceNavy,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(alertColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = alertColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = incident.type.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = alertColor
                    )
                    Text(
                        text = if (isUrgent) "SEVERITY 3 • SIMULATION PAUSED" else "OPERATIONAL ALERT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUrgent) CrimsonError else AmberWarning
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = incident.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimaryDark
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Operator Command Actions:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyanNeon
                )

                Spacer(modifier = Modifier.height(10.dp))

                when (incident.type) {
                    IncidentType.LOW_FUEL -> {
                        Button(
                            onClick = {
                                onResolveLowFuel("DISPATCH_FUEL_TRUCK")
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("resolve_fuel_truck_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Dispatch Mobile Fuel Truck ($120)", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onResolveLowFuel("REROUTE_STATION")
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("resolve_reroute_fuel_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reroute to Nearest Fuel Depot", color = TextPrimaryDark, fontSize = 12.sp)
                        }
                    }

                    IncidentType.BREAKDOWN -> {
                        Button(
                            onClick = {
                                onResolveBreakdown("TOW_TO_BASE")
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("resolve_tow_base_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Tow to Base Maintenance Bay ($350)", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onResolveBreakdown("FIELD_REPAIR")
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("resolve_field_repair_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Emergency On-Site Field Repair ($550)", color = TextPrimaryDark, fontSize = 12.sp)
                        }
                    }

                    IncidentType.STRIKE -> {
                        Button(
                            onClick = {
                                onResolveStrike("TAKE_LOAN")
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("resolve_take_loan_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Emergency Business Bridge Loan (+$8,000)", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onResolveStrike("PAY_BONUS")
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("resolve_pay_bonus_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Pay Labor Peace Bonus ($500)", color = TextPrimaryDark, fontSize = 12.sp)
                        }
                    }

                    else -> {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Acknowledge & Clear Delay", color = CitySurfaceNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Postpone", color = TextSecondaryDark)
            }
        }
    )
}
