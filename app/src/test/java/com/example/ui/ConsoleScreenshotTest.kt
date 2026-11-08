package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.CityFlowShell
import com.example.data.CityRoadNetwork
import com.example.data.ConnectionState
import com.example.model.Campaign
import com.example.model.CityEvent
import com.example.model.CityEventKind
import com.example.model.CityZone
import com.example.model.Contract
import com.example.model.ContractStatus
import com.example.model.ContractTier
import com.example.model.ContractType
import com.example.model.Loan
import com.example.model.Incident
import com.example.model.IncidentType
import com.example.model.LatLng
import com.example.model.LocationPoint
import com.example.model.Payload
import com.example.model.Player
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.StaffStatus
import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import com.example.ui.theme.CityFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the console with representative game state and writes PNGs to `build/screenshots`.
 * These are development aids for visual review — they do not assert pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ConsoleScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val network = CityRoadNetwork()

    @Test
    fun commandScreen() = capture("01-command", AppScreen.COMMAND)

    @Test
    fun dispatchScreen() = capture("02-dispatch", AppScreen.DISPATCH)

    @Test
    fun fleetScreen() = capture("03-fleet", AppScreen.FLEET)

    @Test
    fun companyPersonnel() = capture("04-company-personnel", AppScreen.COMPANY)

    @Test
    fun companyLedger() {
        render(AppScreen.COMPANY)
        composeRule.onNodeWithText("Ledger", substring = true).performClick()
        composeRule.waitForIdle()
        save("05-company-ledger")
    }

    @Test
    fun companyTerritory() {
        render(AppScreen.COMPANY)
        composeRule.onNodeWithText("Territory", substring = true).performClick()
        composeRule.waitForIdle()
        save("06-company-territory")
    }

    @Test
    fun companyMarketing() {
        render(AppScreen.COMPANY)
        composeRule.onNodeWithText("Marketing", substring = true).performClick()
        composeRule.waitForIdle()
        save("07-company-marketing")
    }

    @Test
    fun companyFinance() {
        render(AppScreen.COMPANY)
        composeRule.onNodeWithText("Finance", substring = true).performClick()
        composeRule.waitForIdle()
        save("08-company-finance")
    }

    @Test
    fun `navigation switches destination inside the shell`() {
        var screen by mutableStateOf(AppScreen.COMMAND)
        composeRule.setContent {
            CityFlowTheme(darkTheme = true) {
                CityFlowShell(
                    screen = screen,
                    player = player,
                    vehicles = vehicles,
                    staff = staff.associateBy { it.id },
                    staffList = staff,
                    contracts = contracts,
                    incidents = incidents,
                    zones = zones,
                    transactions = transactions,
                    campaigns = campaigns,
                    currentTick = 437L,
                    isPaused = false,
                    speedMultiplier = 1f,
                    autoDispatchEnabled = true,
                    selectedVehicleId = null,
                    connection = ConnectionState.ONLINE,
                    roadNetwork = network,
                    activeEmergency = null,
                    showSettings = false,
                    onScreen = { screen = it },
                )
            }
        }
        composeRule.onNodeWithTag("command_screen_root").assertExists()
        composeRule.onNodeWithTag("nav_item_fleet").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fleet_screen_root").assertExists()
    }

    @Test
    fun `clock control emits the pause action`() {
        var pauses = 0
        composeRule.setContent {
            CityFlowTheme(darkTheme = true) {
                CityFlowShell(
                    screen = AppScreen.COMMAND,
                    player = player,
                    vehicles = vehicles,
                    staff = staff.associateBy { it.id },
                    staffList = staff,
                    contracts = contracts,
                    incidents = incidents,
                    zones = zones,
                    transactions = transactions,
                    campaigns = campaigns,
                    currentTick = 437L,
                    isPaused = false,
                    speedMultiplier = 1f,
                    autoDispatchEnabled = true,
                    selectedVehicleId = null,
                    connection = ConnectionState.ONLINE,
                    roadNetwork = network,
                    activeEmergency = null,
                    showSettings = false,
                    onTogglePause = { pauses++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Pause simulation").performClick()
        composeRule.waitForIdle()
        assertEquals(1, pauses)
    }

    private fun capture(name: String, screen: AppScreen) {
        render(screen)
        save(name)
    }

    private fun render(screen: AppScreen) {
        composeRule.setContent {
            CityFlowTheme(darkTheme = true) {
                CityFlowShell(
                    screen = screen,
                    player = player,
                    vehicles = vehicles,
                    staff = staff.associateBy { it.id },
                    staffList = staff,
                    contracts = contracts,
                    incidents = incidents,
                    zones = zones,
                    transactions = transactions,
                    campaigns = campaigns,
                    currentTick = 437L,
                    isPaused = false,
                    speedMultiplier = 2f,
                    autoDispatchEnabled = true,
                    selectedVehicleId = "veh_01",
                    connection = ConnectionState.ONLINE,
                    roadNetwork = network,
                    activeEmergency = null,
                    showSettings = false,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun save(name: String) {
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    // --- Fixtures ---------------------------------------------------------------------------

    private val player = Player(
        id = "demo",
        companyName = "Apex City Logistics",
        cash = 24380.5,
        level = 3,
        xp = 1450,
        reputation = 92.0,
        totalRevenue = 68420.0,
        contractsCompleted = 47,
        contractsFailed = 4,
        distanceKm = 1286.0,
        tick = 437,
        fuelPrice = 1.27,
        fuelReserve = 340.0,
        creditLimit = 10500.0,
        loan = Loan(principal = 5000.0, rate = 0.06, takenTick = 300, dueTick = 540, remaining = 3200.0),
        cityEvent = CityEvent(
            kind = CityEventKind.FESTIVAL,
            label = "Street festival — crowds downtown",
            ticksLeft = 18,
            speedFactor = 0.80,
            demandFactor = 1.55,
            rewardFactor = 1.25,
        ),
    )

    private val staff = listOf(
        Staff("staff_drv_01", name = "Sipho Khumalo", role = StaffRole.DRIVER, fatigue = 24.0, skill = 8, status = StaffStatus.ON_SHIFT, assignedVehicleId = "veh_01", salaryPerDay = 135.0),
        Staff("staff_drv_02", name = "Lerato Sithole", role = StaffRole.DRIVER, fatigue = 68.0, skill = 9, status = StaffStatus.ON_SHIFT, assignedVehicleId = "veh_02", salaryPerDay = 165.0),
        Staff("staff_drv_03", name = "Thabo Mthembu", role = StaffRole.DRIVER, fatigue = 84.0, skill = 6, status = StaffStatus.RESTING, restTicks = 42, salaryPerDay = 155.0),
        Staff("staff_dsp_01", name = "David Nkosi", role = StaffRole.DISPATCHER, fatigue = 5.0, skill = 7, status = StaffStatus.ON_SHIFT, salaryPerDay = 150.0),
        Staff("staff_mec_01", name = "Marcus van der Merwe", role = StaffRole.MECHANIC, skill = 9, status = StaffStatus.ON_SHIFT, salaryPerDay = 160.0),
    )

    private val vehicles = listOf(
        Vehicle(
            id = "veh_01", vehicleClass = VehicleClass.VAN, plate = "CA 492-811",
            fuel = 62.0, condition = 78.0, mileage = 4210.0,
            status = VehicleStatus.EN_ROUTE_TO_DROPOFF,
            currentLocation = LatLng(-33.918, 18.427),
            currentRoute = listOf(LatLng(-33.918, 18.427), LatLng(-33.906, 18.419)),
            routeProgress = 0.4f, routeKm = 6.4, travelledKm = 2.5,
            assignedDriverId = "staff_drv_01", currentContractId = "ctr_101",
        ),
        Vehicle(
            id = "veh_02", vehicleClass = VehicleClass.TRUCK, plate = "CA 631-904",
            fuel = 18.0, condition = 44.0, mileage = 18960.0,
            status = VehicleStatus.IDLE,
            currentLocation = LatLng(-33.923, 18.421),
            assignedDriverId = "staff_drv_02",
        ),
        Vehicle(
            id = "veh_03", vehicleClass = VehicleClass.REFRIGERATED, plate = "CA 118-220",
            fuel = 91.0, condition = 96.0, mileage = 980.0,
            status = VehicleStatus.IDLE,
            currentLocation = LatLng(-33.926, 18.418),
            assignedDriverId = "staff_drv_03",
        ),
        Vehicle(
            id = "veh_04", vehicleClass = VehicleClass.SEDAN, plate = "CA 770-133",
            fuel = 47.0, condition = 22.0, mileage = 24800.0,
            status = VehicleStatus.BREAKDOWN,
            currentLocation = LatLng(-33.931, 18.415),
        ),
    )

    private val contracts = listOf(
        Contract(
            id = "ctr_101", type = ContractType.FOOD,
            pickup = LocationPoint(-33.9210, 18.4180, "Long Street Kitchen", "cbd", "RESTAURANT"),
            dropoff = LocationPoint(-33.9100, 18.4150, "Marina Residential", "waterfront", "RESIDENTIAL"),
            payload = Payload(12.0, requiresRefrigeration = true, description = "Food parcel"),
            reward = 168.4, deadlineTick = 452L, status = ContractStatus.IN_PROGRESS,
            assignedVehicleId = "veh_01", assignedDriverId = "staff_drv_01",
            createdAtTick = 430L, routeKm = 6.4, zoneId = "cbd",
        ),
        Contract(
            id = "ctr_102", type = ContractType.CARGO,
            pickup = LocationPoint(-33.9155, 18.4160, "De Waterkant Depot", "cbd", "WAREHOUSE"),
            dropoff = LocationPoint(-33.9290, 18.4400, "Salt River Depot", "woodstock", "WAREHOUSE"),
            payload = Payload(1850.0, description = "Freight pallet"),
            reward = 612.0, deadlineTick = 471L, status = ContractStatus.OPEN,
            createdAtTick = 436L, routeKm = 9.8, zoneId = "cbd", tier = ContractTier.BULK,
        ),
        Contract(
            id = "ctr_103", type = ContractType.PARCEL,
            pickup = LocationPoint(-33.9180, 18.4170, "Cape Quarter Mall", "cbd", "COMMERCIAL"),
            dropoff = LocationPoint(-33.9070, 18.4200, "Clock Tower Precinct", "waterfront", "TRANSIT_HUB"),
            payload = Payload(8.0, description = "Parcel"),
            reward = 94.2, deadlineTick = 462L, status = ContractStatus.OPEN,
            createdAtTick = 437L, routeKm = 3.1, zoneId = "cbd", tier = ContractTier.EXPRESS,
        ),
        Contract(
            id = "ctr_104", type = ContractType.RIDESHARE,
            pickup = LocationPoint(-33.9225, 18.4240, "Adderley Street Transit Hub", "cbd", "TRANSIT_HUB"),
            dropoff = LocationPoint(-33.9046, 18.4180, "V&A Mall", "waterfront", "COMMERCIAL"),
            payload = Payload(75.0, description = "1-2 passengers"),
            reward = 82.0, deadlineTick = 430L, status = ContractStatus.FAILED,
            createdAtTick = 400L, completedAtTick = 431L, routeKm = 4.2, zoneId = "cbd",
        ),
        Contract(
            id = "ctr_105", type = ContractType.FOOD,
            pickup = LocationPoint(-33.9195, 18.4195, "Bree Street Bistro", "cbd", "RESTAURANT"),
            dropoff = LocationPoint(-33.9160, 18.4300, "Foreshore Apartments", "cbd", "RESIDENTIAL"),
            payload = Payload(6.0, description = "Food parcel"),
            reward = 74.5, deadlineTick = 428L, status = ContractStatus.COMPLETED,
            createdAtTick = 404L, completedAtTick = 420L, routeKm = 2.4, zoneId = "cbd",
        ),
    )

    private val incidents = listOf(
        Incident(
            id = "inc_1", vehicleId = "veh_02", type = IncidentType.LOW_FUEL, severity = 2,
            costIfIgnored = 350.0, description = "CA 631-904 is low on fuel (18%)", createdTick = 435L,
        ),
        Incident(
            id = "inc_2", vehicleId = "veh_04", type = IncidentType.BREAKDOWN, severity = 3,
            costIfIgnored = 520.0, description = "CA 770-133 condition critical (22%)", createdTick = 436L,
        ),
    )

    private val transactions = listOf(
        Transaction("tx_1", tick = 436, category = TransactionCategory.REVENUE, amount = 612.0, contractId = "ctr_100", description = "Completed cargo ctr_100"),
        Transaction("tx_2", tick = 434, category = TransactionCategory.FUEL, amount = -148.5, description = "Refuelled CA 631-904"),
        Transaction("tx_3", tick = 430, category = TransactionCategory.SALARY, amount = -765.0, description = "Payroll (430)"),
        Transaction("tx_4", tick = 428, category = TransactionCategory.REVENUE, amount = 168.4, description = "Completed food ctr_098"),
        Transaction("tx_5", tick = 421, category = TransactionCategory.REPAIR, amount = -220.0, description = "Preventative service CA 118-220"),
        Transaction("tx_6", tick = 418, category = TransactionCategory.REVENUE, amount = 94.2, description = "Completed parcel ctr_094"),
        Transaction("tx_7", tick = 410, category = TransactionCategory.EXPANSION, amount = -6500.0, description = "Unlocked Woodstock & Salt River"),
        Transaction("tx_8", tick = 402, category = TransactionCategory.LOAN, amount = 5000.0, description = "Emergency operating loan"),
    )

    private val campaigns = listOf(
        Campaign("cmp_1", "cbd", ticksLeft = 22, spend = 3000.0, demandBoost = 0.5, rewardBoost = 0.25),
        Campaign("cmp_2", "waterfront", ticksLeft = 0, spend = 2500.0, demandBoost = 0.42, rewardBoost = 0.21),
    )

    private val zones: List<CityZone> = network.zones.mapIndexed { index, zone ->
        zone.copy(
            unlocked = index < 3,
            completedContracts = 18 - index * 3,
            failedContracts = index,
            unmetDemand = index * 2,
        )
    }
}
