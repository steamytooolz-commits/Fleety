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
import com.example.model.Transaction
import com.example.model.Vehicle
import com.example.model.VehicleClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen(val title: String) {
    COMMAND("Command"),
    DISPATCH("Dispatch"),
    MAP("Map"),
    FLEET("Fleet"),
    COMPANY("Company"),
}

/**
 * Thin UI shell over the authoritative tick service. Every mutation below is an intent posted to
 * PocketBase; local state is replaced only by server snapshots and realtime deltas.
 */
class CityFlowViewModel : ViewModel() {
    val roadNetwork = CityRoadNetwork()
    val repository = PocketBaseRepository(roadNetwork)

    val player: StateFlow<Player> = repository.player
    val staff: StateFlow<Map<String, Staff>> = repository.staff
    val vehiclesMap: StateFlow<Map<String, Vehicle>> = repository.vehicles
    val contractsMap: StateFlow<Map<String, Contract>> = repository.contracts
    val incidentsMap: StateFlow<Map<String, Incident>> = repository.incidents
    val transactions: StateFlow<List<Transaction>> = repository.transactions
    val zones: StateFlow<List<CityZone>> = repository.zones

    val vehiclesList: StateFlow<List<Vehicle>> = vehiclesMap.map { it.values.sortedBy { v -> v.plate } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val contractsList: StateFlow<List<Contract>> = contractsMap.map { it.values.sortedByDescending { c -> c.createdAtTick } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val incidentsList: StateFlow<List<Incident>> = incidentsMap.map { it.values.sortedByDescending { i -> i.createdTick } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val staffList: StateFlow<List<Staff>> = staff.map { it.values.sortedBy { s -> s.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentTick: StateFlow<Long> = player.map { it.tick }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val isPaused: StateFlow<Boolean> = player.map { it.paused }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val speedMultiplier: StateFlow<Float> = player.map { it.speed.toFloat() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val autoDispatchEnabled: StateFlow<Boolean> = player.map { it.autoDispatchEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val connection = repository.connection

    private val _currentScreen = MutableStateFlow(AppScreen.COMMAND)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _activeEmergency = MutableStateFlow<Incident?>(null)
    val activeEmergency: StateFlow<Incident?> = _activeEmergency.asStateFlow()

    /** True when the app paused the clock itself to surface a severity-3 incident. */
    private var autoPausedForEmergency = false

    init {
        repository.start()
        viewModelScope.launch {
            repository.observeIncidents().collect { event ->
                if (event is RealtimeEvent.Created && event.item.severity >= 3 && !event.item.resolved) {
                    surfaceEmergency(event.item)
                }
            }
        }
        // Severity-3 incidents already open when the app connects.
        viewModelScope.launch {
            repository.incidents.collect { map ->
                if (_activeEmergency.value == null) {
                    map.values.firstOrNull { it.severity >= 3 && !it.resolved }?.let { surfaceEmergency(it) }
                }
            }
        }
    }

    private fun surfaceEmergency(incident: Incident) {
        _activeEmergency.value = incident
        if (!player.value.paused) {
            autoPausedForEmergency = true
            repository.setPaused(true)
        }
    }

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectVehicle(vehicleId: String) {
        _selectedVehicleId.value = vehicleId
    }

    fun clearVehicleSelection() {
        _selectedVehicleId.value = null
    }

    fun togglePause() {
        if (isPaused.value) autoPausedForEmergency = false
        repository.setPaused(!isPaused.value)
    }

    fun setSpeedMultiplier(multiplier: Float) {
        repository.sendIntent(
            "SET_SPEED",
            org.json.JSONObject().put("speed", multiplier.toDouble().coerceIn(1.0, 4.0))
        )
    }

    fun stepTick() {
        repository.forceStep()
    }

    fun toggleAutoDispatch() {
        repository.setAutoDispatch(!autoDispatchEnabled.value)
    }

    fun assignContract(contractId: String, vehicleId: String) {
        repository.assignContract(contractId, vehicleId)
    }

    fun spawnContract() {
        repository.spawnContract()
    }

    fun purchaseVehicle(vClass: VehicleClass) {
        repository.purchaseVehicle(vClass)
    }

    fun refuelVehicle(vehicleId: String) {
        repository.refuelVehicle(vehicleId)
    }

    fun serviceVehicle(vehicleId: String) {
        repository.serviceVehicle(vehicleId)
    }

    fun hireStaff(name: String, role: StaffRole, skill: Int) {
        repository.hireStaff(name, role, skill)
    }

    fun toggleStaffShift(staffId: String) {
        repository.toggleStaffShift(staffId)
    }

    fun assignDriver(staffId: String, vehicleId: String?) {
        repository.assignDriver(staffId, vehicleId)
    }

    fun unlockZone(zoneId: String) {
        repository.unlockZone(zoneId)
    }

    fun setActiveEmergency(incident: Incident?) {
        if (incident == null) dismissEmergency() else _activeEmergency.value = incident
    }

    /** Close the incident sheet and release a clock pause the app applied itself. */
    fun dismissEmergency() {
        _activeEmergency.value = null
        if (autoPausedForEmergency) {
            autoPausedForEmergency = false
            if (isPaused.value) repository.setPaused(false)
        }
    }

    fun resolveLowFuel(vehicleId: String, action: String) {
        resolveForVehicle(vehicleId, action)
    }

    fun resolveBreakdown(vehicleId: String, action: String) {
        resolveForVehicle(vehicleId, action)
    }

    fun resolveStrike(action: String) {
        val incident = _activeEmergency.value ?: return
        repository.resolveIncident(incident.id, action)
        dismissEmergency()
    }

    private fun resolveForVehicle(vehicleId: String, action: String) {
        val incident = _activeEmergency.value
            ?: repository.incidents.value.values.firstOrNull { it.vehicleId == vehicleId && !it.resolved }
        if (incident != null) repository.resolveIncident(incident.id, action)
        dismissEmergency()
    }

    fun saveSettings(pbUrl: String, osrmUrl: String, autoDispatch: Boolean) {
        roadNetwork.osrmBaseUrl = osrmUrl
        repository.configure(pbUrl)
        if (autoDispatch != autoDispatchEnabled.value) {
            repository.setAutoDispatch(autoDispatch)
        }
    }

    override fun onCleared() {
        repository.stop()
        super.onCleared()
    }
}
