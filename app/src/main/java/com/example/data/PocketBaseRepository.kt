package com.example.data

import com.example.model.CityZone
import com.example.model.Contract
import com.example.model.ContractStatus
import com.example.model.ContractType
import com.example.model.Incident
import com.example.model.IncidentType
import com.example.model.Player
import com.example.model.RealtimeEvent
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.StaffStatus
import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.UUID

class PocketBaseRepository(
    private val roadNetwork: CityRoadNetwork
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var pocketBaseUrl: String = "http://10.0.2.2:8090"
    var isRemoteConnected: Boolean = false

    // In-memory snapshot states
    private val _player = MutableStateFlow(Player())
    val player: StateFlow<Player> = _player.asStateFlow()

    private val _staff = MutableStateFlow<Map<String, Staff>>(emptyMap())
    val staff: StateFlow<Map<String, Staff>> = _staff.asStateFlow()

    private val _vehicles = MutableStateFlow<Map<String, Vehicle>>(emptyMap())
    val vehicles: StateFlow<Map<String, Vehicle>> = _vehicles.asStateFlow()

    private val _contracts = MutableStateFlow<Map<String, Contract>>(emptyMap())
    val contracts: StateFlow<Map<String, Contract>> = _contracts.asStateFlow()

    private val _incidents = MutableStateFlow<Map<String, Incident>>(emptyMap())
    val incidents: StateFlow<Map<String, Incident>> = _incidents.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _zones = MutableStateFlow<List<CityZone>>(roadNetwork.zones)
    val zones: StateFlow<List<CityZone>> = _zones.asStateFlow()

    // Realtime event channels for SSE deltas
    private val vehicleEvents = MutableSharedFlow<RealtimeEvent<Vehicle>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val contractEvents = MutableSharedFlow<RealtimeEvent<Contract>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val incidentEvents = MutableSharedFlow<RealtimeEvent<Incident>>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Idempotency cache
    private val processedIdempotencyKeys = mutableSetOf<String>()

    init {
        seedInitialState()
    }

    private fun seedInitialState() {
        val baseLoc = roadNetwork.nodes["cbd_center"]?.location ?: roadNetwork.zones.first().center

        // Initial Staff: 1 driver, 1 mechanic, 1 dispatcher
        val initialStaff = listOf(
            Staff(
                id = "staff_01",
                name = "Sipho Khumalo",
                role = StaffRole.DRIVER,
                fatigue = 15.0,
                skill = 8,
                status = StaffStatus.ON_SHIFT,
                assignedVehicleId = "veh_01",
                salaryPerDay = 135.0
            ),
            Staff(
                id = "staff_02",
                name = "Elena Rostova",
                role = StaffRole.DRIVER,
                fatigue = 5.0,
                skill = 6,
                status = StaffStatus.ON_SHIFT,
                assignedVehicleId = "veh_02",
                salaryPerDay = 120.0
            ),
            Staff(
                id = "staff_03",
                name = "David Nkosi",
                role = StaffRole.DISPATCHER,
                fatigue = 20.0,
                skill = 7,
                status = StaffStatus.ON_SHIFT,
                salaryPerDay = 150.0
            ),
            Staff(
                id = "staff_04",
                name = "Marcus Van Der Merwe",
                role = StaffRole.MECHANIC,
                fatigue = 0.0,
                skill = 9,
                status = StaffStatus.ON_SHIFT,
                salaryPerDay = 160.0
            )
        )
        _staff.value = initialStaff.associateBy { it.id }

        // Initial Vehicles: 1 Van, 1 Sedan
        val initialVehicles = listOf(
            Vehicle(
                id = "veh_01",
                vehicleClass = VehicleClass.VAN,
                plate = "CA-492-811",
                fuel = 88.0,
                condition = 94.0,
                mileage = 1420.0,
                status = VehicleStatus.IDLE,
                currentLocation = baseLoc,
                assignedDriverId = "staff_01"
            ),
            Vehicle(
                id = "veh_02",
                vehicleClass = VehicleClass.SEDAN,
                plate = "CA-631-904",
                fuel = 92.0,
                condition = 98.0,
                mileage = 750.0,
                status = VehicleStatus.IDLE,
                currentLocation = roadNetwork.nodes["cbd_buitengracht"]?.location ?: baseLoc,
                assignedDriverId = "staff_02"
            )
        )
        _vehicles.value = initialVehicles.associateBy { it.id }

        // Initial Transactions
        val startingTx = listOf(
            Transaction(
                id = "tx_seed_01",
                tick = 0,
                category = TransactionCategory.REVENUE,
                amount = 18500.0,
                description = "Initial Logistics Venture Capital"
            )
        )
        _transactions.value = startingTx
    }

    // --- Coroutine-first Realtime Streams as specified in Prompt Section 5 ---

    fun observeVehicles(): Flow<RealtimeEvent<Vehicle>> = callbackFlow {
        val job = scope.launch {
            vehicleEvents.collect { event ->
                trySend(event)
            }
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    fun observeContracts(): Flow<RealtimeEvent<Contract>> = callbackFlow {
        val job = scope.launch {
            contractEvents.collect { event ->
                trySend(event)
            }
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    fun observeIncidents(): Flow<RealtimeEvent<Incident>> = callbackFlow {
        val job = scope.launch {
            incidentEvents.collect { event ->
                trySend(event)
            }
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    // --- State Mutations (Authoritative / Idempotent) ---

    fun emitVehicleUpdate(vehicle: Vehicle) {
        val current = _vehicles.value.toMutableMap()
        current[vehicle.id] = vehicle
        _vehicles.value = current
        vehicleEvents.tryEmit(RealtimeEvent.Updated(vehicle))
    }

    fun emitVehicleCreated(vehicle: Vehicle) {
        val current = _vehicles.value.toMutableMap()
        current[vehicle.id] = vehicle
        _vehicles.value = current
        vehicleEvents.tryEmit(RealtimeEvent.Created(vehicle))
    }

    fun emitContractCreated(contract: Contract) {
        val current = _contracts.value.toMutableMap()
        current[contract.id] = contract
        _contracts.value = current
        contractEvents.tryEmit(RealtimeEvent.Created(contract))
    }

    fun emitContractUpdated(contract: Contract) {
        val current = _contracts.value.toMutableMap()
        current[contract.id] = contract
        _contracts.value = current
        contractEvents.tryEmit(RealtimeEvent.Updated(contract))
    }

    fun emitIncidentCreated(incident: Incident) {
        val current = _incidents.value.toMutableMap()
        current[incident.id] = incident
        _incidents.value = current
        incidentEvents.tryEmit(RealtimeEvent.Created(incident))
    }

    fun emitIncidentResolved(incidentId: String) {
        val current = _incidents.value.toMutableMap()
        val inc = current[incidentId] ?: return
        val resolved = inc.copy(resolved = true)
        current[incidentId] = resolved
        _incidents.value = current
        incidentEvents.tryEmit(RealtimeEvent.Updated(resolved))
    }

    fun updateStaff(staffMember: Staff) {
        val current = _staff.value.toMutableMap()
        current[staffMember.id] = staffMember
        _staff.value = current
    }

    fun addStaff(staffMember: Staff) {
        val current = _staff.value.toMutableMap()
        current[staffMember.id] = staffMember
        _staff.value = current
    }

    fun updatePlayerCash(delta: Double, description: String, category: TransactionCategory, contractId: String? = null, currentTick: Long = 0L) {
        val currPlayer = _player.value
        val newCash = (currPlayer.cash + delta).coerceAtLeast(0.0)
        _player.value = currPlayer.copy(cash = newCash)

        val tx = Transaction(
            id = "tx_" + UUID.randomUUID().toString().take(8),
            tick = currentTick,
            category = category,
            amount = delta,
            contractId = contractId,
            description = description
        )
        _transactions.value = _transactions.value + tx
    }

    fun unlockZone(zoneId: String, currentTick: Long) {
        val currentZones = _zones.value.toMutableList()
        val idx = currentZones.indexOfFirst { it.zoneId == zoneId }
        if (idx != -1) {
            val z = currentZones[idx]
            if (!z.unlocked && _player.value.cash >= z.unlockCost) {
                updatePlayerCash(-z.unlockCost, "Expansion: Unlocked ${z.name}", TransactionCategory.EXPANSION, currentTick = currentTick)
                currentZones[idx] = z.copy(unlocked = true)
                _zones.value = currentZones
            }
        }
    }

    fun incrementZoneCompletedContract(zoneId: String) {
        val currentZones = _zones.value.toMutableList()
        val idx = currentZones.indexOfFirst { it.zoneId == zoneId }
        if (idx != -1) {
            val z = currentZones[idx]
            currentZones[idx] = z.copy(completedContracts = z.completedContracts + 1)
            _zones.value = currentZones
        }
    }

    /**
     * Checks idempotency key to prevent double actions on SSE flaps
     */
    fun checkAndRecordIdempotency(key: String): Boolean {
        synchronized(processedIdempotencyKeys) {
            if (processedIdempotencyKeys.contains(key)) return false
            processedIdempotencyKeys.add(key)
            return true
        }
    }
}
