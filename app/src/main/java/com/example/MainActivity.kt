package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.CityFlowViewModel
import com.example.ui.dashboard.AlertFeed
import com.example.ui.dashboard.KpiStrip
import com.example.ui.dispatch.DispatchScreen
import com.example.ui.emergency.EmergencyDialog
import com.example.ui.expansion.ExpansionScreen
import com.example.ui.fleet.FleetScreen
import com.example.ui.map.CityMapView
import com.example.ui.settings.SettingsDialog
import com.example.ui.staff.StaffScreen
import com.example.ui.theme.CityBorderNavy
import com.example.ui.theme.CityCardNavy
import com.example.ui.theme.CityDarkNavy
import com.example.ui.theme.CityFlowTheme
import com.example.ui.theme.CitySurfaceNavy
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CityFlowTheme(darkTheme = true) {
                CityFlowApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityFlowApp(
    viewModel: CityFlowViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehiclesList.collectAsStateWithLifecycle()
    val staff by viewModel.staff.collectAsStateWithLifecycle()
    val contracts by viewModel.contractsList.collectAsStateWithLifecycle()
    val incidents by viewModel.incidentsList.collectAsStateWithLifecycle()
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()

    val currentTick by viewModel.currentTick.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val speedMultiplier by viewModel.speedMultiplier.collectAsStateWithLifecycle()
    val autoDispatchEnabled by viewModel.autoDispatchEnabled.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val activeEmergency by viewModel.activeEmergency.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }

    // System BackHandler: return to Map if in sub-screens
    BackHandler(enabled = currentScreen != AppScreen.OPERATIONS) {
        viewModel.setScreen(AppScreen.OPERATIONS)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = CityDarkNavy,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanNeon.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CityFlow",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimaryDark
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = CyanNeon) {
                                    Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CitySurfaceNavy)
                                }
                            }
                            Text(
                                text = "${player.companyName} • ${connection.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (connection == com.example.data.ConnectionState.ONLINE) EmeraldSuccess else TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Cash pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CityCardNavy,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CityBorderNavy)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(EmeraldSuccess, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$${String.format("%,.0f", player.cash)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldSuccess
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("app_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondaryDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CitySurfaceNavy,
                    titleContentColor = TextPrimaryDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CitySurfaceNavy,
                contentColor = CyanNeon,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_bottom_nav")
            ) {
                AppScreen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    val icon = when (screen) {
                        AppScreen.OPERATIONS -> Icons.Default.Map
                        AppScreen.DISPATCH -> Icons.Default.Assignment
                        AppScreen.FLEET -> Icons.Default.LocalShipping
                        AppScreen.STAFF -> Icons.Default.People
                        AppScreen.EXPANSION -> Icons.Default.AutoGraph
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.setScreen(screen) },
                        icon = { Icon(icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CitySurfaceNavy,
                            selectedTextColor = CyanNeon,
                            indicatorColor = CyanNeon,
                            unselectedIconColor = TextSecondaryDark,
                            unselectedTextColor = TextMutedDark
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.OPERATIONS -> {
                    // Full Screen Live Map with Floating HUD
                    Box(modifier = Modifier.fillMaxSize()) {
                        CityMapView(
                            vehicles = vehicles,
                            roadNetwork = viewModel.roadNetwork,
                            selectedVehicleId = selectedVehicleId,
                            onVehicleSelect = { viewModel.selectVehicle(it) }
                        )

                        // Floating KPI Strip & Alert Feed
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            val activeContractsCount = contracts.count {
                                it.status == com.example.model.ContractStatus.ASSIGNED ||
                                it.status == com.example.model.ContractStatus.IN_PROGRESS
                            }
                            val idleCount = vehicles.count { it.status == com.example.model.VehicleStatus.IDLE }
                            val onShiftStaffCount = staff.values.count { it.status == com.example.model.StaffStatus.ON_SHIFT }

                            KpiStrip(
                                cash = player.cash,
                                todayRevenue = contracts.filter { it.status == com.example.model.ContractStatus.COMPLETED }.sumOf { it.reward },
                                activeContractsCount = activeContractsCount,
                                idleVehiclesCount = idleCount,
                                totalVehiclesCount = vehicles.size,
                                staffOnShiftCount = onShiftStaffCount,
                                currentTick = currentTick,
                                isPaused = isPaused,
                                speedMultiplier = speedMultiplier,
                                onTogglePause = { viewModel.togglePause() },
                                onSetSpeed = { viewModel.setSpeedMultiplier(it) },
                                onStepTick = { viewModel.stepTick() }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            AlertFeed(
                                incidents = incidents,
                                onIncidentClick = { viewModel.setActiveEmergency(it) }
                            )
                        }
                    }
                }

                AppScreen.DISPATCH -> {
                    DispatchScreen(
                        contracts = contracts,
                        vehicles = vehicles,
                        staff = staff,
                        currentTick = currentTick,
                        autoDispatchEnabled = autoDispatchEnabled,
                        onToggleAutoDispatch = { viewModel.toggleAutoDispatch() },
                        onManualAssign = { cId, vId -> viewModel.assignContract(cId, vId) },
                        onSpawnContract = { viewModel.spawnContract() }
                    )
                }

                AppScreen.FLEET -> {
                    FleetScreen(
                        vehicles = vehicles,
                        staff = staff,
                        playerCash = player.cash,
                        onPurchaseVehicle = { viewModel.purchaseVehicle(it) },
                        onRefuelVehicle = { viewModel.refuelVehicle(it) },
                        onServiceVehicle = { viewModel.serviceVehicle(it) }
                    )
                }

                AppScreen.STAFF -> {
                    StaffScreen(
                        staffList = staff.values.toList(),
                        vehicles = vehicles,
                        playerCash = player.cash,
                        onHireStaff = { name, role, skill -> viewModel.hireStaff(name, role, skill) },
                        onToggleShift = { viewModel.toggleStaffShift(it) },
                        onAssignVehicle = { _, _ -> }
                    )
                }

                AppScreen.EXPANSION -> {
                    ExpansionScreen(
                        zones = zones,
                        playerCash = player.cash,
                        currentTick = currentTick,
                        onUnlockZone = { viewModel.unlockZone(it) }
                    )
                }
            }
        }
    }

    // Emergency Action Modal (pauses clock if severity 3)
    if (activeEmergency != null) {
        val emergency = activeEmergency!!
        EmergencyDialog(
            incident = emergency,
            onResolveLowFuel = { action ->
                emergency.vehicleId?.let { viewModel.resolveLowFuel(it, action) }
            },
            onResolveBreakdown = { action ->
                emergency.vehicleId?.let { viewModel.resolveBreakdown(it, action) }
            },
            onResolveStrike = { action ->
                viewModel.resolveStrike(action)
            },
            onDismiss = { viewModel.setActiveEmergency(null) }
        )
    }

    // System Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentPbUrl = viewModel.repository.pocketBaseUrl,
            currentOsrmUrl = viewModel.roadNetwork.osrmBaseUrl,
            autoDispatchEnabled = autoDispatchEnabled,
            onSaveSettings = { pb, osrm, auto -> viewModel.saveSettings(pb, osrm, auto) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}
