package com.example.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CityBorderNavy
import com.example.ui.theme.CityCardNavy
import com.example.ui.theme.CitySurfaceNavy
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun SettingsDialog(
    currentPbUrl: String,
    currentOsrmUrl: String,
    autoDispatchEnabled: Boolean,
    onSaveSettings: (pbUrl: String, osrmUrl: String, autoDispatch: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var pbUrl by remember { mutableStateOf(currentPbUrl) }
    var osrmUrl by remember { mutableStateOf(currentOsrmUrl) }
    var autoDispatch by remember { mutableStateOf(autoDispatchEnabled) }

    fun safeDismiss() {
        focusManager.clearFocus()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { safeDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp),
        containerColor = CitySurfaceNavy,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("System & Server Architecture", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CityFlow Native Engine • PocketBase & OSRM Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = pbUrl,
                    onValueChange = { pbUrl = it },
                    label = { Text("PocketBase SSE URL", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("pb_url_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CityCardNavy,
                        unfocusedContainerColor = CityCardNavy,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedIndicatorColor = CyanNeon,
                        unfocusedIndicatorColor = CityBorderNavy
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = osrmUrl,
                    onValueChange = { osrmUrl = it },
                    label = { Text("OSRM Docker Routing Endpoint", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("osrm_url_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CityCardNavy,
                        unfocusedContainerColor = CityCardNavy,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedIndicatorColor = CyanNeon,
                        unfocusedIndicatorColor = CityBorderNavy
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Deterministic Auto-Dispatch", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Auto-match contracts with idle fleet", color = TextSecondaryDark, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoDispatch,
                        onCheckedChange = { autoDispatch = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.4f))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Security: Superuser credentials never ship in APK. All client mutations enforce idempotency keys.",
                        color = TextMutedDark,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSaveSettings(pbUrl, osrmUrl, autoDispatch)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_settings_btn")
            ) {
                Text("Save Configuration", color = CitySurfaceNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { safeDismiss() }) {
                Text("Cancel", color = TextSecondaryDark)
            }
        }
    )
}
