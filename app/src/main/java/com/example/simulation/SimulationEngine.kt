package com.example.simulation

import com.example.data.CityRoadNetwork
import com.example.data.PocketBaseRepository
import com.example.model.Contract
import com.example.model.ContractStatus
import com.example.model.ContractType
import com.example.model.Incident
import com.example.model.IncidentType
import com.example.model.Payload
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.StaffStatus
import com.example.model.TransactionCategory
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class SimulationEngine(
    val repository: PocketBaseRepository,
    val roadNetwork: CityRoadNetwork
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val _currentTick = MutableStateFlow(0L)
    val currentTick: StateFlow<Long> = _currentTick.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _tickSpeedMultiplier = MutableStateFlow(1f) // 1x, 2x, 5x
    val tickSpeedMultiplier: StateFlow<Float> = _tickSpeedMultiplier.asStateFlow()

    private val _autoDispatchEnabled = MutableStateFlow(true)
    val autoDispatchEnabled: StateFlow<Boolean> = _autoDispatchEnabled.asStateFlow()

    // PRNG seeded deterministically for demand generation (Section 3.5)
    private var demandPrng = Random(4242)

    init {
        startTickEngine()
        seedInitialContracts()
    }

    fun startTickEngine() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                if (!_isPaused.value) {
                    processTick()
                }
                val delayMs = (1000L / _tickSpeedMultiplier.value).toLong().coerceIn(150L, 2000L)
                delay(delayMs)
            }
        }
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setSpeedMultiplier(multiplier: Float) {
        _tickSpeedMultiplier.value = multiplier
    }

    fun toggleAutoDispatch() {
        _autoDispatchEnabled.value = !_autoDispatchEnabled.value
    }

    fun advanceManualTick() {
        scope.launch {
            processTick()
        }
    }

    private fun seedInitialContracts() {
        spawnContract(forcedType = ContractType.FOOD)
        spawnContract(forcedType = ContractType.RIDESHARE)
        spawnContract(forcedType = ContractType.PARCEL)
    }

    /**
     * Authoritative Tick Processor (1 tick = 1 simulated minute)
     */
    suspend fun processTick() {
        val tick = _currentTick.value + 1
        _currentTick.value = tick

        val vehicles = repository.vehicles.value.toMutableMap()
        val staffMap = repository.staff.value.toMutableMap()
        val contracts = repository.contracts.value.toMutableMap()
        val zones = repository.zones.value

        // 1. Process Vehicle FSM and Route Progress
        for ((vehId, vehicle) in vehicles) {
            val updatedVehicle = processVehicleFsm(vehicle, staffMap, contracts, zones, tick)
            vehicles[vehId] = updatedVehicle
            repository.emitVehicleUpdate(updatedVehicle)
        }

        // 2. Check Contract Deadlines
        for ((contractId, contract) in contracts) {
            if (contract.status == ContractStatus.OPEN || contract.status == ContractStatus.ASSIGNED || contract.status == ContractStatus.IN_PROGRESS) {
                if (tick > contract.deadlineTick) {
                    val failedContract = contract.copy(status = ContractStatus.FAILED)
                    contracts[contractId] = failedContract
                    repository.emitContractUpdated(failedContract)

                    // Release assigned vehicle if failed before completion
                    if (contract.assignedVehicleId != null) {
                        val v = vehicles[contract.assignedVehicleId]
                        if (v != null && v.currentContractId == contract.id) {
                            val released = v.copy(
                                status = VehicleStatus.IDLE,
                                currentContractId = null,
                                currentRoute = emptyList(),
                                routeProgress = 0f
                            )
                            vehicles[v.id] = released
                            repository.emitVehicleUpdate(released)
                        }
                    }
                }
            }
        }

        // 3. Deterministic Auto-Dispatch (if dispatcher present or auto-dispatch active)
        if (_autoDispatchEnabled.value) {
            val openContracts = contracts.values.filter { it.status == ContractStatus.OPEN }
            for (openContract in openContracts) {
                val match = MatchingEngine.findBestVehicle(vehicles.values.toList(), staffMap, openContract)
                if (match != null) {
                    val (bestVeh, _) = match
                    assignContractToVehicle(openContract.id, bestVeh.id, "auto_dispatch_$tick")
                }
            }
        }

        // 4. Zone Demand Generation (Poisson Process)
        if (tick % 4L == 0L && contracts.values.count { it.status == ContractStatus.OPEN } < 10) {
            spawnContract()
        }

        // 5. Daily Payroll & Upkeep (every 30 ticks = 1 simulated day)
        if (tick % 30L == 0L) {
            processDailyPayrollAndUpkeep(tick, staffMap, vehicles)
        }
    }

    private suspend fun processVehicleFsm(
        vehicle: Vehicle,
        staffMap: MutableMap<String, Staff>,
        contracts: MutableMap<String, Contract>,
        zones: List<com.example.model.CityZone>,
        tick: Long
    ): Vehicle {
        var v = vehicle
        val driver = v.assignedDriverId?.let { staffMap[it] }

        // Find current zone traffic index
        val currentZone = zones.find { it.bounds.contains(v.currentLocation) } ?: zones.first()
        val trafficIndex = currentZone.trafficIndex

        when (v.status) {
            VehicleStatus.IDLE -> {
                // Driver rests slightly at base/idle
                if (driver != null && driver.fatigue > 0.0) {
                    val restedDriver = driver.copy(fatigue = (driver.fatigue - 0.4).coerceAtLeast(0.0))
                    staffMap[driver.id] = restedDriver
                    repository.updateStaff(restedDriver)
                }

                // If driver is exhausted, force rest
                if (driver != null && driver.fatigue >= 80.0) {
                    v = v.copy(status = VehicleStatus.FORCED_REST)
                }
            }

            VehicleStatus.FORCED_REST -> {
                if (driver != null) {
                    val restedDriver = driver.copy(fatigue = (driver.fatigue - 2.5).coerceAtLeast(0.0))
                    staffMap[driver.id] = restedDriver
                    repository.updateStaff(restedDriver)
                    if (restedDriver.fatigue <= 15.0) {
                        v = v.copy(status = VehicleStatus.IDLE)
                    }
                } else {
                    v = v.copy(status = VehicleStatus.IDLE)
                }
            }

            VehicleStatus.BREAKDOWN -> {
                // Stopped, awaits tow or field repair
            }

            VehicleStatus.DIVERT_TO_REFUEL -> {
                // Travel to fuel station and fill tank
                v = v.copy(
                    fuel = (v.fuel + 20.0).coerceAtMost(100.0),
                    lowFuelTicks = 0
                )
                if (v.fuel >= 95.0) {
                    repository.updatePlayerCash(-90.0, "Refuel ${v.plate}", TransactionCategory.FUEL, currentTick = tick)
                    v = v.copy(status = VehicleStatus.IDLE)
                }
            }

            VehicleStatus.EN_ROUTE_TO_PICKUP -> {
                v = advanceRouteMovement(v, driver, trafficIndex, tick)
                if (v.routeProgress >= 1f) {
                    // Arrived at pickup
                    val contract = v.currentContractId?.let { contracts[it] }
                    if (contract != null) {
                        if (contract.type == ContractType.RIDESHARE) {
                            // Instant passenger board
                            val dropoffRoute = roadNetwork.findPath(contract.pickup.toLatLng(), contract.dropoff.toLatLng())
                            v = v.copy(
                                status = VehicleStatus.EN_ROUTE_TO_DROPOFF,
                                currentLocation = contract.pickup.toLatLng(),
                                currentRoute = dropoffRoute,
                                routeProgress = 0f
                            )
                            val inProgress = contract.copy(status = ContractStatus.IN_PROGRESS)
                            contracts[contract.id] = inProgress
                            repository.emitContractUpdated(inProgress)
                        } else {
                            // Cargo/food loading state
                            v = v.copy(
                                status = VehicleStatus.LOADING,
                                currentLocation = contract.pickup.toLatLng(),
                                routeProgress = 0f
                            )
                        }
                    } else {
                        v = v.copy(status = VehicleStatus.IDLE)
                    }
                }
            }

            VehicleStatus.LOADING -> {
                // Takes 1 tick to load
                val contract = v.currentContractId?.let { contracts[it] }
                if (contract != null) {
                    val dropoffRoute = roadNetwork.findPath(contract.pickup.toLatLng(), contract.dropoff.toLatLng())
                    v = v.copy(
                        status = VehicleStatus.EN_ROUTE_TO_DROPOFF,
                        currentRoute = dropoffRoute,
                        routeProgress = 0f
                    )
                    val inProgress = contract.copy(status = ContractStatus.IN_PROGRESS)
                    contracts[contract.id] = inProgress
                    repository.emitContractUpdated(inProgress)
                } else {
                    v = v.copy(status = VehicleStatus.IDLE)
                }
            }

            VehicleStatus.EN_ROUTE_TO_DROPOFF -> {
                v = advanceRouteMovement(v, driver, trafficIndex, tick)
                if (v.routeProgress >= 1f) {
                    val contract = v.currentContractId?.let { contracts[it] }
                    if (contract != null) {
                        if (contract.type == ContractType.RIDESHARE) {
                            // Completed immediately
                            v = completeContract(v, contract, contracts, tick)
                        } else {
                            v = v.copy(
                                status = VehicleStatus.UNLOADING,
                                currentLocation = contract.dropoff.toLatLng(),
                                routeProgress = 0f
                            )
                        }
                    } else {
                        v = v.copy(status = VehicleStatus.IDLE)
                    }
                }
            }

            VehicleStatus.UNLOADING -> {
                val contract = v.currentContractId?.let { contracts[it] }
                if (contract != null) {
                    v = completeContract(v, contract, contracts, tick)
                } else {
                    v = v.copy(status = VehicleStatus.IDLE)
                }
            }

            VehicleStatus.AT_PICKUP,
            VehicleStatus.MAINTENANCE -> {
                // Restoring condition
                v = v.copy(condition = (v.condition + 10.0).coerceAtMost(100.0))
                if (v.condition >= 99.0) {
                    v = v.copy(status = VehicleStatus.IDLE)
                }
            }
        }

        // Check Low Fuel Incident Trigger (Prompt Section 7.1)
        if (v.fuel < 15.0 && v.status != VehicleStatus.BREAKDOWN && v.status != VehicleStatus.DIVERT_TO_REFUEL) {
            val newTicks = v.lowFuelTicks + 1
            v = v.copy(lowFuelTicks = newTicks)

            if (newTicks == 1) {
                // Raise low fuel emergency
                repository.emitIncidentCreated(
                    Incident(
                        id = "inc_fuel_" + UUID.randomUUID().toString().take(6),
                        vehicleId = v.id,
                        type = IncidentType.LOW_FUEL,
                        severity = 2,
                        costIfIgnored = 350.0,
                        description = "Vehicle ${v.plate} fuel is critical (${v.fuel.toInt()}%). Breakdown imminent in 3 ticks if ignored.",
                        createdTick = tick
                    )
                )
            } else if (newTicks >= 3) {
                // Ignored for 3 ticks -> Breakdown occurs
                v = v.copy(status = VehicleStatus.BREAKDOWN)
                repository.emitIncidentCreated(
                    Incident(
                        id = "inc_break_" + UUID.randomUUID().toString().take(6),
                        vehicleId = v.id,
                        type = IncidentType.BREAKDOWN,
                        severity = 3, // Pauses simulation clock!
                        costIfIgnored = 500.0,
                        description = "Vehicle ${v.plate} ran completely dry and engine stalled in traffic! Tow or mobile repair required.",
                        createdTick = tick
                    )
                )
                setPaused(true) // Severity 3 pauses clock
            }
        }

        // Condition breakdown check: if condition < 20%
        if (v.condition < 20.0 && v.status != VehicleStatus.BREAKDOWN && v.status != VehicleStatus.MAINTENANCE) {
            if (Random.nextDouble() < 0.15) {
                v = v.copy(status = VehicleStatus.BREAKDOWN)
                repository.emitIncidentCreated(
                    Incident(
                        id = "inc_mech_" + UUID.randomUUID().toString().take(6),
                        vehicleId = v.id,
                        type = IncidentType.BREAKDOWN,
                        severity = 3,
                        costIfIgnored = 450.0,
                        description = "Engine failure on ${v.plate} (Condition ${v.condition.toInt()}%). Tow to base or call mobile mechanic.",
                        createdTick = tick
                    )
                )
                setPaused(true)
            }
        }

        return v
    }

    private fun advanceRouteMovement(
        vehicle: Vehicle,
        driver: Staff?,
        trafficIndex: Double,
        tick: Long
    ): Vehicle {
        var v = vehicle
        val route = v.currentRoute
        if (route.size < 2) return v

        // Distance advance per tick:
        // 1 tick = 1 min. At 50 km/h, distance in 1 min is ~0.83 km.
        // Scale with traffic congestion: speed = baseSpeed * (1 - trafficIndex * 0.5)
        val speedKmPerTick = (0.75 * (1.0 - trafficIndex * 0.45)).coerceIn(0.2, 1.2)
        val totalRouteDist = calculateRouteDistance(route).coerceAtLeast(0.5)
        val progressDelta = (speedKmPerTick / totalRouteDist).toFloat()
        val newProgress = (v.routeProgress + progressDelta).coerceAtMost(1f)

        // Interpolated position and heading
        val newLocation = CityRoadNetwork.interpolatePosition(route, newProgress)
        val newHeading = CityRoadNetwork.calculateHeading(route, newProgress)

        // Fuel burn calculation (Section 7.1):
        // burn = base_rate * (1 + payload_weight * 0.0005) * (1 + traffic_index * 0.1) / skill
        val payloadWeight = 200.0
        val skill = (driver?.skill ?: 5).toDouble().coerceAtLeast(1.0)
        val burn = v.vehicleClass.baseBurnRate * (1.0 + payloadWeight * 0.0005) * (1.0 + trafficIndex * 0.1) / (skill * 0.35)
        val newFuel = (v.fuel - burn).coerceAtLeast(0.0)

        // Condition wear: ~0.08% per tick driven
        val newCondition = (v.condition - 0.08).coerceAtLeast(0.0)
        val newMileage = v.mileage + speedKmPerTick

        // Driver fatigue increase: +0.6 per tick
        if (driver != null) {
            val updatedDriver = driver.copy(fatigue = (driver.fatigue + 0.6).coerceAtMost(100.0))
            repository.updateStaff(updatedDriver)
        }

        return v.copy(
            currentLocation = newLocation,
            routeProgress = newProgress,
            heading = newHeading,
            fuel = newFuel,
            condition = newCondition,
            mileage = newMileage
        )
    }

    private fun completeContract(
        vehicle: Vehicle,
        contract: Contract,
        contracts: MutableMap<String, Contract>,
        tick: Long
    ): Vehicle {
        val completed = contract.copy(
            status = ContractStatus.COMPLETED,
            completedAtTick = tick
        )
        contracts[contract.id] = completed
        repository.emitContractUpdated(completed)

        // Reward payout
        repository.updatePlayerCash(
            delta = contract.reward,
            description = "Contract Payout: ${contract.type.label} (${contract.pickup.label} -> ${contract.dropoff.label})",
            category = TransactionCategory.REVENUE,
            contractId = contract.id,
            currentTick = tick
        )

        // Zone stats increment
        repository.incrementZoneCompletedContract(contract.dropoff.zoneId)

        return vehicle.copy(
            status = VehicleStatus.IDLE,
            currentContractId = null,
            currentRoute = emptyList(),
            routeProgress = 0f
        )
    }

    private fun calculateRouteDistance(route: List<com.example.model.LatLng>): Double {
        var dist = 0.0
        for (i in 0 until route.size - 1) {
            dist += route[i].distanceTo(route[i + 1])
        }
        return dist
    }

    fun assignContractToVehicle(contractId: String, vehicleId: String, idempotencyKey: String): Boolean {
        if (!repository.checkAndRecordIdempotency(idempotencyKey)) return false

        val contract = repository.contracts.value[contractId] ?: return false
        val vehicle = repository.vehicles.value[vehicleId] ?: return false
        if (vehicle.status != VehicleStatus.IDLE) return false

        scope.launch {
            // Find path from vehicle current location to pickup
            val routeToPickup = roadNetwork.getRouteWithOsrmFallback(vehicle.currentLocation, contract.pickup.toLatLng())

            val updatedContract = contract.copy(
                status = ContractStatus.ASSIGNED,
                assignedVehicleId = vehicle.id,
                assignedDriverId = vehicle.assignedDriverId
            )
            repository.emitContractUpdated(updatedContract)

            val updatedVehicle = vehicle.copy(
                status = VehicleStatus.EN_ROUTE_TO_PICKUP,
                currentContractId = contract.id,
                currentRoute = routeToPickup,
                routeProgress = 0f
            )
            repository.emitVehicleUpdate(updatedVehicle)
        }
        return true
    }

    fun spawnContract(forcedType: ContractType? = null) {
        val unlockedZones = repository.zones.value.filter { it.unlocked }
        if (unlockedZones.isEmpty()) return

        val zone = unlockedZones.random(demandPrng)
        val type = forcedType ?: when (demandPrng.nextInt(4)) {
            0 -> ContractType.FOOD
            1 -> ContractType.RIDESHARE
            2 -> ContractType.PARCEL
            else -> ContractType.CARGO
        }

        // Zone-based Poisson POI selection as specified in Section 7.3:
        val pickup = when (type) {
            ContractType.FOOD -> roadNetwork.pois.filter { it.poiType == "RESTAURANT" && it.zoneId == zone.zoneId }.randomOrNull(demandPrng)
                ?: roadNetwork.pois.first { it.poiType == "RESTAURANT" }
            ContractType.RIDESHARE -> roadNetwork.pois.filter { (it.poiType == "TRANSIT_HUB" || it.poiType == "COMMERCIAL") && it.zoneId == zone.zoneId }.randomOrNull(demandPrng)
                ?: roadNetwork.pois.first { it.poiType == "TRANSIT_HUB" }
            ContractType.CARGO -> roadNetwork.pois.filter { (it.poiType == "WAREHOUSE" || it.poiType == "INDUSTRIAL") }.randomOrNull(demandPrng)
                ?: roadNetwork.pois.first { it.poiType == "WAREHOUSE" }
            ContractType.PARCEL -> roadNetwork.pois.filter { it.zoneId == zone.zoneId }.randomOrNull(demandPrng)
                ?: roadNetwork.pois.random(demandPrng)
        }

        val dropoff = roadNetwork.pois.filter { it.label != pickup.label }.random(demandPrng)

        val distanceKm = pickup.toLatLng().distanceTo(dropoff.toLatLng()).coerceAtLeast(1.5)
        val reward = when (type) {
            ContractType.FOOD -> 85.0 + distanceKm * 28.0
            ContractType.RIDESHARE -> 120.0 + distanceKm * 32.0
            ContractType.PARCEL -> 95.0 + distanceKm * 24.0
            ContractType.CARGO -> 320.0 + distanceKm * 45.0
        }

        val payload = when (type) {
            ContractType.FOOD -> Payload(weightKg = 12.0, requiresRefrigeration = demandPrng.nextBoolean(), description = "Gourmet meal parcels")
            ContractType.RIDESHARE -> Payload(weightKg = 160.0, requiresRefrigeration = false, description = "2 Passengers + Luggage")
            ContractType.PARCEL -> Payload(weightKg = 45.0, requiresRefrigeration = false, description = "Tech hardware delivery")
            ContractType.CARGO -> Payload(weightKg = 1250.0, requiresRefrigeration = false, description = "Industrial replacement turbines")
        }

        val deadline = _currentTick.value + 40L + (distanceKm * 6).toLong()

        val contract = Contract(
            id = "cnt_" + UUID.randomUUID().toString().take(7),
            type = type,
            pickup = pickup,
            dropoff = dropoff,
            payload = payload,
            reward = Math.round(reward * 100.0) / 100.0,
            deadlineTick = deadline,
            createdAtTick = _currentTick.value
        )
        repository.emitContractCreated(contract)
    }

    private fun processDailyPayrollAndUpkeep(
        tick: Long,
        staffMap: Map<String, Staff>,
        vehicles: Map<String, Vehicle>
    ) {
        val totalSalaries = staffMap.values.sumOf { it.salaryPerDay }
        val totalUpkeep = vehicles.values.sumOf { it.vehicleClass.dailyUpkeep }
        val totalDeduction = totalSalaries + totalUpkeep

        val currentCash = repository.player.value.cash
        if (currentCash < totalDeduction) {
            // Cannot meet payroll -> Labor union strike incident! (Prompt Section 8 & 10)
            repository.updatePlayerCash(-currentCash, "Emergency Partial Payroll (Insolvent)", TransactionCategory.SALARY, currentTick = tick)
            repository.emitIncidentCreated(
                Incident(
                    id = "inc_strike_" + UUID.randomUUID().toString().take(6),
                    vehicleId = null,
                    type = IncidentType.STRIKE,
                    severity = 3, // Pauses simulation!
                    costIfIgnored = 1200.0,
                    description = "Staff strike! Unpaid payroll ($${totalDeduction.toInt()}) caused staff walkout. Take emergency loan or pay bonus to resolve.",
                    createdTick = tick
                )
            )
            setPaused(true)
        } else {
            repository.updatePlayerCash(-totalSalaries, "Daily Staff Payroll", TransactionCategory.SALARY, currentTick = tick)
            repository.updatePlayerCash(-totalUpkeep, "Daily Fleet Maintenance Upkeep", TransactionCategory.REPAIR, currentTick = tick)
        }
    }

    // --- Emergency Action Resolvers (Prompt Section 10) ---

    fun resolveLowFuel(vehicleId: String, action: String) {
        val v = repository.vehicles.value[vehicleId] ?: return
        when (action) {
            "DISPATCH_FUEL_TRUCK" -> {
                // Cost $120, refills fuel to 80% immediately on site
                repository.updatePlayerCash(-120.0, "Mobile Fuel Truck for ${v.plate}", TransactionCategory.FUEL, currentTick = _currentTick.value)
                val updated = v.copy(fuel = 80.0, lowFuelTicks = 0)
                repository.emitVehicleUpdate(updated)
            }
            "REROUTE_STATION" -> {
                // Divert to station
                val nearestStation = roadNetwork.pois.filter { it.poiType == "FUEL_STATION" }
                    .minByOrNull { it.toLatLng().distanceTo(v.currentLocation) } ?: roadNetwork.pois.first()
                val route = roadNetwork.findPath(v.currentLocation, nearestStation.toLatLng())
                val updated = v.copy(
                    status = VehicleStatus.DIVERT_TO_REFUEL,
                    currentRoute = route,
                    routeProgress = 0f,
                    lowFuelTicks = 0
                )
                repository.emitVehicleUpdate(updated)
            }
        }
    }

    fun resolveBreakdown(vehicleId: String, action: String) {
        val v = repository.vehicles.value[vehicleId] ?: return
        val baseLoc = roadNetwork.nodes["cbd_center"]?.location ?: v.currentLocation

        when (action) {
            "TOW_TO_BASE" -> {
                // Tow cost $350 + downtime
                repository.updatePlayerCash(-350.0, "Heavy Tow Service for ${v.plate}", TransactionCategory.TOW, currentTick = _currentTick.value)
                val updated = v.copy(
                    status = VehicleStatus.MAINTENANCE,
                    currentLocation = baseLoc,
                    currentRoute = emptyList(),
                    routeProgress = 0f,
                    condition = 45.0,
                    fuel = 50.0,
                    lowFuelTicks = 0,
                    currentContractId = null
                )
                repository.emitVehicleUpdate(updated)
            }
            "FIELD_REPAIR" -> {
                // Mobile repair van $550 (partial fix on site)
                repository.updatePlayerCash(-550.0, "Emergency Field Repair for ${v.plate}", TransactionCategory.REPAIR, currentTick = _currentTick.value)
                val updated = v.copy(
                    status = VehicleStatus.IDLE,
                    condition = 65.0,
                    fuel = 40.0,
                    lowFuelTicks = 0
                )
                repository.emitVehicleUpdate(updated)
            }
        }
    }

    fun resolveStrike(action: String) {
        when (action) {
            "TAKE_LOAN" -> {
                // Emergency loan $8,000 cash injected
                repository.updatePlayerCash(8000.0, "Emergency Business Bridge Loan", TransactionCategory.REVENUE, currentTick = _currentTick.value)
            }
            "PAY_BONUS" -> {
                // Pay $500 bonus
                repository.updatePlayerCash(-500.0, "Labor Peace Bonus", TransactionCategory.SALARY, currentTick = _currentTick.value)
            }
        }
        setPaused(false)
    }

    fun purchaseVehicle(vehicleClass: VehicleClass, name: String): Boolean {
        val price = vehicleClass.purchasePrice
        if (repository.player.value.cash < price) return false

        val baseLoc = roadNetwork.nodes["cbd_center"]?.location ?: roadNetwork.zones.first().center
        val newId = "veh_" + UUID.randomUUID().toString().take(6)
        val plate = "CA-" + Random.nextInt(100, 999) + "-" + Random.nextInt(100, 999)

        repository.updatePlayerCash(-price, "Acquired ${vehicleClass.label} ($plate)", TransactionCategory.PURCHASE, currentTick = _currentTick.value)

        val newVehicle = Vehicle(
            id = newId,
            vehicleClass = vehicleClass,
            plate = plate,
            fuel = 100.0,
            condition = 100.0,
            mileage = 0.0,
            status = VehicleStatus.IDLE,
            currentLocation = baseLoc
        )
        repository.emitVehicleCreated(newVehicle)
        return true
    }

    fun hireStaff(name: String, role: StaffRole, skill: Int): Boolean {
        val cost = 350.0
        if (repository.player.value.cash < cost) return false

        val newId = "staff_" + UUID.randomUUID().toString().take(6)
        repository.updatePlayerCash(-cost, "Recruited $name (${role.name})", TransactionCategory.SALARY, currentTick = _currentTick.value)

        val newStaff = Staff(
            id = newId,
            name = name,
            role = role,
            skill = skill,
            status = StaffStatus.ON_SHIFT,
            salaryPerDay = when (role) {
                StaffRole.DRIVER -> 120.0 + skill * 5.0
                StaffRole.DISPATCHER -> 140.0 + skill * 6.0
                StaffRole.MECHANIC -> 150.0 + skill * 6.0
                StaffRole.MANAGER -> 200.0 + skill * 8.0
            }
        )
        repository.addStaff(newStaff)
        return true
    }
}
