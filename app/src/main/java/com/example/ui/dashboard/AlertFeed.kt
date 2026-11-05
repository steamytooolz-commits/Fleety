package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CityBorderNavy
import com.example.ui.theme.CityCardNavy
import com.example.ui.theme.CitySurfaceNavy
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun AlertFeed(
    incidents: List<Incident>,
    onIncidentClick: (Incident) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeIncidents = incidents.filter { !it.resolved }
    if (activeIncidents.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("alert_feed_column"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (incident in activeIncidents.take(3)) {
            val isUrgent = incident.severity >= 3
            val alertColor = if (isUrgent) CrimsonError else AmberWarning

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIncidentClick(incident) }
                    .testTag("alert_card_${incident.id}"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CitySurfaceNavy),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    alertColor.copy(alpha = 0.8f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = incident.type.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = alertColor
                            )
                            if (isUrgent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CrimsonError
                                ) {
                                    Text(
                                        text = "CRITICAL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = incident.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onIncidentClick(incident) },
                        colors = ButtonDefaults.buttonColors(containerColor = alertColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("alert_action_btn_${incident.id}")
                    ) {
                        Text(
                            text = "RESOLVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CitySurfaceNavy
                        )
                    }
                }
            }
        }
    }
}
