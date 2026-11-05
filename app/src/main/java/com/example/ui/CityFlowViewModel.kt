package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CityRoadNetwork
import com.example.data.PocketBaseRepository
import com.example.model.CityZone
import com.example.model.Contract
import com.example.model.Incident
import com.example.model.Player
import com.example.model.RealtimeEvent
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.StaffStatus
import com.example.model.Transaction
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import com.example.simulation.SimulationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen(val title: String) {
    OPERATIONS("Live Map"),
    DISPATCH("Dispatch"),
    FLEET("Fleet"),
    STAFF("Staff"),
    EXPANSION("Expansion")
}

class CityFlowViewModel : ViewModel() {
    val roadNetwork = CityRoadNetwork()
    val repository = PocketBaseRepository(roadNetwork)
    val simulationEngine = SimulationEngine(repository, roadNetwork)

    val player: StateFlow<Player> = repository.player
    val staff: StateFlow<Map<String, Staff>> = repository.staff
    val vehiclesMap: StateFlow<Map<String, Vehicle>> = repository.vehicles
    val contractsMap: StateFlow<Map<String, Contract>> = repository.contracts
    val incidentsMap: StateFlow<Map<String, Incident>> = repository.incidents
    val transactions: StateFlow<List<Transaction>> = repository.transactions
    val zones: StateFlow<List<CityZone>> = repository.zones

    val vehiclesList: StateFlow<List<Vehicle>> = vehiclesMap.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val contractsList: StateFlow<List<Contract>> = contractsMap.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val incidentsList: StateFlow<List<Incident>> = incidentsMap.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentTick: StateFlow<Long> = simulationEngine.currentTick
    val isPaused: StateFlow<Boolean> = simulationEngine.isPaused
    val speedMultiplier: StateFlow<Float> = simulationEngine.tickSpeedMultiplier
    val autoDispatchEnabled: StateFlow<Boolean> = simulationEngine.autoDispatchEnabled

    private val _currentScreen = MutableStateFlow(AppScreen.OPERATIONS)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<String?>("veh_01")
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _activeEmergency = MutableStateFlow<Incident?>(null)
    val activeEmergency: StateFlow<Incident?> = _activeEmergency.asStateFlow()

    init {
        // Observe real-time incident events for severity == 3 popup (Prompt Section 5 & 10)
        viewModelScope.launch {
            repository.observeIncidents().collect { event ->
                if (event is RealtimeEvent.Created && event.item.severity >= 3) {
                    _activeEmergency.value = event.item
                }
            }
        }
    }

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectVehicle(vehicleId: String) {
        _selectedVehicleId.value = vehicleId
    }

    fun togglePause() {
        simulationEngine.setPaused(!isPaused.value)
    }

    fun setSpeedMultiplier(multiplier: Float) {
        simulationEngine.setSpeedMultiplier(multiplier)
    }

    fun stepTick() {
        simulationEngine.advanceManualTick()
    }

    fun toggleAutoDispatch() {
        simulationEngine.toggleAutoDispatch()
    }

    fun assignContract(contractId: String, vehicleId: String) {
        val idempotencyKey = "manual_assign_" + UUID.randomUUID().toString()
        simulationEngine.assignContractToVehicle(contractId, vehicleId, idempotencyKey)
    }

    fun spawnContract() {
        simulationEngine.spawnContract()
    }

    fun purchaseVehicle(vClass: VehicleClass) {
        simulationEngine.purchaseVehicle(vClass, vClass.label)
    }

    fun refuelVehicle(vehicleId: String) {
        val v = repository.vehicles.value[vehicleId] ?: return
        if (player.value.cash >= 90.0) {
            repository.updatePlayerCash(-90.0, "Refuel ${v.plate}", com.example.model.TransactionCategory.FUEL, currentTick = currentTick.value)
            repository.emitVehicleUpdate(v.copy(fuel = 100.0, lowFuelTicks = 0))
        }
    }

    fun serviceVehicle(vehicleId: String) {
        val v = repository.vehicles.value[vehicleId] ?: return
        if (player.value.cash >= 220.0) {
            repository.updatePlayerCash(-220.0, "Preventative Service ${v.plate}", com.example.model.TransactionCategory.REPAIR, currentTick = currentTick.value)
            repository.emitVehicleUpdate(v.copy(condition = 100.0))
        }
    }

    fun hireStaff(name: String, role: StaffRole, skill: Int) {
        simulationEngine.hireStaff(name, role, skill)
    }

    fun toggleStaffShift(staffId: String) {
        val member = repository.staff.value[staffId] ?: return
        val newStatus = if (member.status == StaffStatus.ON_SHIFT) StaffStatus.RESTING else StaffStatus.ON_SHIFT
        repository.updateStaff(member.copy(status = newStatus))
    }

    fun unlockZone(zoneId: String) {
        repository.unlockZone(zoneId, currentTick.value)
    }

    fun setActiveEmergency(incident: Incident?) {
        _activeEmergency.value = incident
    }

    fun resolveLowFuel(vehicleId: String, action: String) {
        simulationEngine.resolveLowFuel(vehicleId, action)
        _activeEmergency.value?.let { repository.emitIncidentResolved(it.id) }
        _activeEmergency.value = null
    }

    fun resolveBreakdown(vehicleId: String, action: String) {
        simulationEngine.resolveBreakdown(vehicleId, action)
        _activeEmergency.value?.let { repository.emitIncidentResolved(it.id) }
        _activeEmergency.value = null
    }

    fun resolveStrike(action: String) {
        simulationEngine.resolveStrike(action)
        _activeEmergency.value?.let { repository.emitIncidentResolved(it.id) }
        _activeEmergency.value = null
    }

    fun saveSettings(pbUrl: String, osrmUrl: String, autoDispatch: Boolean) {
        repository.pocketBaseUrl = pbUrl
        roadNetwork.osrmBaseUrl = osrmUrl
        if (autoDispatch != autoDispatchEnabled.value) {
            simulationEngine.toggleAutoDispatch()
        }
    }
}
