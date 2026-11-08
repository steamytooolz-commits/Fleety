package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.Mint
import com.example.ui.theme.Muted
import com.example.ui.theme.Paper
import com.example.ui.theme.Slate
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceHigh

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

    Dialog(
        onDismissRequest = { safeDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(20.dp),
            shape = RoundedCornerShape(26.dp),
            color = SurfaceBase,
            border = BorderStroke(1.dp, Hairline),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Connection", style = MaterialTheme.typography.titleLarge, color = Paper)
                        Text(
                            "Point the client at your PocketBase and OSRM services",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("END POINTS", style = MaterialTheme.typography.labelSmall, color = Mint)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = pbUrl,
                    onValueChange = { pbUrl = it },
                    label = { Text("PocketBase URL") },
                    placeholder = { Text("http://10.0.2.2:8090", color = Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pb_url_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = osrmUrl,
                    onValueChange = { osrmUrl = it },
                    label = { Text("OSRM routing URL") },
                    placeholder = { Text("http://10.0.2.2:5000", color = Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("osrm_url_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceHigh.copy(alpha = 0.55f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic dispatch", style = MaterialTheme.typography.titleSmall, color = Paper)
                        Text(
                            "Let the matching engine assign idle assets",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    Switch(
                        checked = autoDispatch,
                        onCheckedChange = { autoDispatch = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Mint,
                            uncheckedThumbColor = Slate,
                            uncheckedTrackColor = SurfaceHigh,
                            uncheckedBorderColor = Hairline,
                        ),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Superuser credentials never ship in the APK. Every client mutation carries an " +
                            "idempotency key and is validated by the tick service.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        onClick = { safeDismiss() },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Save",
                        onClick = {
                            focusManager.clearFocus()
                            onSaveSettings(pbUrl.trim(), osrmUrl.trim(), autoDispatch)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_settings_btn"),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Dns, contentDescription = null, tint = Muted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "CityFlow tick service · PocketBase 0.40 · OSRM MLD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceHigh.copy(alpha = 0.6f),
    unfocusedContainerColor = SurfaceHigh.copy(alpha = 0.35f),
    focusedTextColor = Paper,
    unfocusedTextColor = Paper,
    focusedLabelColor = Mint,
    unfocusedLabelColor = Slate,
    cursorColor = Mint,
    focusedIndicatorColor = Mint.copy(alpha = 0.7f),
    unfocusedIndicatorColor = Hairline,
)
