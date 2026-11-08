package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CityRoadNetwork
import com.example.data.ConnectionState
import com.example.model.CityZone
import com.example.model.Contract
import com.example.model.Incident
import com.example.model.Player
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.Transaction
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.ui.AppScreen
import com.example.ui.CityFlowViewModel
import com.example.ui.command.CommandScreen
import com.example.ui.company.CompanyScreen
import com.example.ui.components.ConsoleBackground
import com.example.ui.components.Dot
import com.example.ui.components.compactMoney
import com.example.ui.dispatch.DispatchScreen
import com.example.ui.emergency.EmergencyDialog
import com.example.ui.fleet.FleetScreen
import com.example.ui.map.MapScreen
import com.example.ui.settings.SettingsDialog
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
import com.example.ui.theme.Slate
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceRaised

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.example.ui.theme.CityFlowTheme(darkTheme = true) {
                CityFlowApp()
            }
        }
    }
}

@Composable
fun CityFlowApp(
    viewModel: CityFlowViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehiclesList.collectAsStateWithLifecycle()
    val staff by viewModel.staff.collectAsStateWithLifecycle()
    val staffList by viewModel.staffList.collectAsStateWithLifecycle()
    val contracts by viewModel.contractsList.collectAsStateWithLifecycle()
    val incidents by viewModel.incidentsList.collectAsStateWithLifecycle()
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val campaigns by viewModel.campaigns.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()

    val currentTick by viewModel.currentTick.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val speedMultiplier by viewModel.speedMultiplier.collectAsStateWithLifecycle()
    val autoDispatchEnabled by viewModel.autoDispatchEnabled.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val activeEmergency by viewModel.activeEmergency.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = currentScreen != AppScreen.COMMAND) {
        viewModel.setScreen(AppScreen.COMMAND)
    }

    CityFlowShell(
        screen = currentScreen,
        player = player,
        vehicles = vehicles,
        staff = staff,
        staffList = staffList,
        contracts = contracts,
        incidents = incidents,
        zones = zones,
        transactions = transactions,
        campaigns = campaigns,
        currentTick = currentTick,
        isPaused = isPaused,
        speedMultiplier = speedMultiplier,
        autoDispatchEnabled = autoDispatchEnabled,
        selectedVehicleId = selectedVehicleId,
        connection = connection,
        roadNetwork = viewModel.roadNetwork,
        activeEmergency = activeEmergency,
        showSettings = showSettingsDialog,
        onScreen = { viewModel.setScreen(it) },
        onTogglePause = { viewModel.togglePause() },
        onSetSpeed = { viewModel.setSpeedMultiplier(it) },
        onStepTick = { viewModel.stepTick() },
        onSelectVehicle = { viewModel.selectVehicle(it) },
        onClearSelection = { viewModel.clearVehicleSelection() },
        onOpenIncident = { viewModel.setActiveEmergency(it) },
        onDismissEmergency = { viewModel.dismissEmergency() },
        onResolveLowFuel = { vehicleId, action -> viewModel.resolveLowFuel(vehicleId, action) },
        onResolveBreakdown = { vehicleId, action -> viewModel.resolveBreakdown(vehicleId, action) },
        onResolveStrike = { viewModel.resolveStrike(it) },
        onAssignContract = { contractId, vehicleId -> viewModel.assignContract(contractId, vehicleId) },
        onToggleAutoDispatch = { viewModel.toggleAutoDispatch() },
        onSpawnContract = { viewModel.spawnContract() },
        onPurchaseVehicle = { viewModel.purchaseVehicle(it) },
        onRefuelVehicle = { viewModel.refuelVehicle(it) },
        onServiceVehicle = { viewModel.scheduleService(it) },
        onBuyFuel = { viewModel.buyDepotFuel(it) },
        onStartCampaign = { zoneId, budget -> viewModel.startCampaign(zoneId, budget) },
        onTakeLoan = { viewModel.takeLoan(it) },
        onRepayLoan = { viewModel.repayLoan(it) },
        onHireStaff = { name, role, skill -> viewModel.hireStaff(name, role, skill) },
        onToggleShift = { viewModel.toggleStaffShift(it) },
        onAssignDriver = { staffId, vehicleId -> viewModel.assignDriver(staffId, vehicleId) },
        onUnlockZone = { viewModel.unlockZone(it) },
        onOpenSettings = { showSettingsDialog = true },
        onCloseSettings = { showSettingsDialog = false },
        onSaveSettings = { pb, osrm, auto ->
            viewModel.saveSettings(pb, osrm, auto)
            showSettingsDialog = false
        },
    )
}

/**
 * Stateless application shell. Keeping it free of the view model lets the whole console be rendered
 * in screenshot tests with fixture state.
 */
@Composable
fun CityFlowShell(
    screen: AppScreen,
    player: Player,
    vehicles: List<Vehicle>,
    staff: Map<String, Staff>,
    staffList: List<Staff>,
    contracts: List<Contract>,
    incidents: List<Incident>,
    zones: List<CityZone>,
    transactions: List<Transaction>,
    campaigns: List<com.example.model.Campaign> = emptyList(),
    currentTick: Long,
    isPaused: Boolean,
    speedMultiplier: Float,
    autoDispatchEnabled: Boolean,
    selectedVehicleId: String?,
    connection: ConnectionState,
    roadNetwork: CityRoadNetwork,
    activeEmergency: Incident?,
    showSettings: Boolean,
    onScreen: (AppScreen) -> Unit = {},
    onTogglePause: () -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onStepTick: () -> Unit = {},
    onSelectVehicle: (String) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onOpenIncident: (Incident) -> Unit = {},
    onDismissEmergency: () -> Unit = {},
    onResolveLowFuel: (String, String) -> Unit = { _, _ -> },
    onResolveBreakdown: (String, String) -> Unit = { _, _ -> },
    onResolveStrike: (String) -> Unit = {},
    onAssignContract: (String, String) -> Unit = { _, _ -> },
    onToggleAutoDispatch: () -> Unit = {},
    onSpawnContract: () -> Unit = {},
    onPurchaseVehicle: (VehicleClass) -> Unit = {},
    onRefuelVehicle: (String) -> Unit = {},
    onServiceVehicle: (String) -> Unit = {},
    onBuyFuel: (Double) -> Unit = {},
    onStartCampaign: (String, Double) -> Unit = { _, _ -> },
    onTakeLoan: (Double) -> Unit = {},
    onRepayLoan: (Double) -> Unit = {},
    onHireStaff: (String, StaffRole, Int) -> Unit = { _, _, _ -> },
    onToggleShift: (String) -> Unit = {},
    onAssignDriver: (String, String?) -> Unit = { _, _ -> },
    onUnlockZone: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCloseSettings: () -> Unit = {},
    onSaveSettings: (String, String, Boolean) -> Unit = { _, _, _ -> },
) {
    val openIncidents = incidents.count { !it.resolved }

    ConsoleBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0),
            containerColor = Color.Transparent,
            topBar = {
                CityTopBar(
                    companyName = player.companyName,
                    cash = player.cash,
                    connection = connection,
                    isPaused = isPaused,
                    onOpenSettings = onOpenSettings,
                )
            },
            bottomBar = {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    CityNavBar(
                        current = screen,
                        incidents = openIncidents,
                        onSelect = onScreen,
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (screen) {
                    AppScreen.COMMAND -> CommandScreen(
                        player = player,
                        vehicles = vehicles,
                        contracts = contracts,
                        staff = staff,
                        incidents = incidents,
                        transactions = transactions,
                        currentTick = currentTick,
                        isPaused = isPaused,
                        speedMultiplier = speedMultiplier,
                        autoDispatchEnabled = autoDispatchEnabled,
                        connection = connection,
                        onTogglePause = onTogglePause,
                        onSetSpeed = onSetSpeed,
                        onStepTick = onStepTick,
                        onOpenIncident = onOpenIncident,
                        onOpenDispatch = { onScreen(AppScreen.DISPATCH) },
                        onOpenFleet = { onScreen(AppScreen.FLEET) },
                        onOpenCompany = { onScreen(AppScreen.COMPANY) },
                        onBuyFuel = onBuyFuel,
                    )

                    AppScreen.DISPATCH -> DispatchScreen(
                        contracts = contracts,
                        vehicles = vehicles,
                        staff = staff,
                        currentTick = currentTick,
                        autoDispatchEnabled = autoDispatchEnabled,
                        onToggleAutoDispatch = onToggleAutoDispatch,
                        onManualAssign = onAssignContract,
                        onSpawnContract = onSpawnContract,
                    )

                    AppScreen.MAP -> MapScreen(
                        vehicles = vehicles,
                        roadNetwork = roadNetwork,
                        staff = staff,
                        contracts = contracts,
                        selectedVehicleId = selectedVehicleId,
                        currentTick = currentTick,
                        isPaused = isPaused,
                        connection = connection,
                        onVehicleSelect = onSelectVehicle,
                        onClearSelection = onClearSelection,
                        onTogglePause = onTogglePause,
                        onOpenFleet = { onScreen(AppScreen.FLEET) },
                    )

                    AppScreen.FLEET -> FleetScreen(
                        vehicles = vehicles,
                        staff = staff,
                        playerCash = player.cash,
                        onPurchaseVehicle = onPurchaseVehicle,
                        onRefuelVehicle = onRefuelVehicle,
                        onServiceVehicle = onServiceVehicle,
                        onAssignDriver = onAssignDriver,
                        fuelPrice = player.fuelPrice,
                        fuelReserve = player.fuelReserve,
                        onBuyFuel = onBuyFuel,
                    )

                    AppScreen.COMPANY -> CompanyScreen(
                        player = player,
                        staff = staffList,
                        vehicles = vehicles,
                        contracts = contracts,
                        transactions = transactions,
                        zones = zones,
                        campaigns = campaigns,
                        currentTick = currentTick,
                        onHireStaff = onHireStaff,
                        onToggleShift = onToggleShift,
                        onAssignDriver = onAssignDriver,
                        onUnlockZone = onUnlockZone,
                        onStartCampaign = onStartCampaign,
                        onTakeLoan = onTakeLoan,
                        onRepayLoan = onRepayLoan,
                    )
                }
            }
        }
    }

    if (activeEmergency != null) {
        EmergencyDialog(
            incident = activeEmergency,
            onResolveLowFuel = { action ->
                activeEmergency.vehicleId?.let { onResolveLowFuel(it, action) }
            },
            onResolveBreakdown = { action ->
                activeEmergency.vehicleId?.let { onResolveBreakdown(it, action) }
            },
            onResolveStrike = onResolveStrike,
            onDismiss = onDismissEmergency,
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentPbUrl = "http://10.0.2.2:8090",
            currentOsrmUrl = "http://10.0.2.2:5000",
            autoDispatchEnabled = autoDispatchEnabled,
            onSaveSettings = onSaveSettings,
            onDismiss = onCloseSettings,
        )
    }
}

@Composable
private fun CityTopBar(
    companyName: String,
    cash: Double,
    connection: ConnectionState,
    isPaused: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(Mint, Iris))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.LocalShipping,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CityFlow",
                    style = MaterialTheme.typography.titleMedium,
                    color = Paper,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "OPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Iris,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Iris.copy(alpha = 0.14f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(
                    color = when (connection) {
                        ConnectionState.ONLINE -> if (isPaused) Amber else Mint
                        ConnectionState.CONNECTING -> Amber
                        ConnectionState.OFFLINE -> Coral
                    },
                    size = 5,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = companyName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 1,
                )
            }
        }

        Surface(
            shape = PillShape,
            color = Mint.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Mint.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Mint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(compactMoney(cash), style = NumericStyle, color = Mint)
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(PillShape)
                .clickable { onOpenSettings() }
                .testTag("app_settings_btn"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Slate, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun CityNavBar(
    current: AppScreen,
    incidents: Int,
    onSelect: (AppScreen) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("main_bottom_nav"),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceRaised.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppScreen.entries.forEach { destination ->
                val selected = current == destination
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Mint.copy(alpha = 0.13f) else Color.Transparent)
                        .clickable { onSelect(destination) }
                        .padding(vertical = 8.dp)
                        .testTag("nav_item_${destination.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Icon(
                            screenIcon(destination),
                            contentDescription = destination.title,
                            tint = if (selected) Mint else Muted,
                            modifier = Modifier.size(20.dp),
                        )
                        if (destination == AppScreen.COMMAND && incidents > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(PillShape)
                                    .background(Coral),
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        destination.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Mint else Muted,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun screenIcon(screen: AppScreen): ImageVector = when (screen) {
    AppScreen.COMMAND -> Icons.Rounded.Dashboard
    AppScreen.DISPATCH -> Icons.Rounded.Assignment
    AppScreen.MAP -> Icons.Rounded.Map
    AppScreen.FLEET -> Icons.Rounded.LocalShipping
    AppScreen.COMPANY -> Icons.Rounded.Business
}
