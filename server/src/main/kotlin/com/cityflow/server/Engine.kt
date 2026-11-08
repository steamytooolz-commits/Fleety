package com.cityflow.server

import kotlinx.serialization.json.*
import kotlin.math.max
import kotlin.math.round

interface RouteProvider { fun route(from: LngLat, to: LngLat): Route? }
data class Route(val geometry: List<LngLat>, val km: Double, val minutes: Double)

data class Intent(val key: String, val type: String, val payload: JsonObject = JsonObject(emptyMap()))
data class IntentOutcome(val world: World, val accepted: Boolean, val message: String)

class Engine(private val cfg: SimConfig = SimConfig()) {

    private companion object {
        const val RECRUITMENT_FEE = 350.0
    }

    fun step(input: World, routes: RouteProvider): TickResult {
        if (input.paused && !input.stepOnce) return TickResult(input, emptyList())
        return Run(input, routes).run()
    }

    fun apply(input: World, intent: Intent, routes: RouteProvider): IntentOutcome {
        var w = input
        fun tx(cat: TxCategory, amount: Double, desc: String, id: String? = null, key: String? = null) {
            w = w.copy(
                cash = (w.cash + amount).let { if (it < 0) 0.0 else round(it * 100) / 100 },
                ledger = w.ledger + Transaction("tx_${w.tick}_${w.ledger.size}", w.tick, cat, round(amount * 100) / 100, id, desc, key),
            )
        }
        val p = intent.payload
        fun str(k: String) = p[k]?.jsonPrimitive?.contentOrNull
        fun num(k: String) = p[k]?.jsonPrimitive?.doubleOrNull
        return when (intent.type.uppercase()) {
            "ASSIGN_CONTRACT" -> {
                val cid = str("contract_id"); val vid = str("vehicle_id")
                val c = w.contracts.find { it.id == cid && it.status == ContractStatus.OPEN }
                    ?: return IntentOutcome(w, false, "Contract is not open")
                val v = w.vehicles.find { it.id == vid && it.status == VehicleStatus.IDLE }
                    ?: return IntentOutcome(w, false, "Vehicle is not idle")
                val d = w.staff.find { it.id == v.driverId && it.role == Role.DRIVER && it.status == StaffStatus.ON_SHIFT && it.fatigue <= cfg.fatigueRest }
                    ?: return IntentOutcome(w, false, "Vehicle has no rested driver on shift")
                if (Matching.suitability(c.type, c.payload, v.vehicleClass) == null) return IntentOutcome(w, false, "Vehicle cannot carry this contract")
                val rt = routes.route(v.location, c.pickup.point()) ?: return IntentOutcome(w, false, "Routing unavailable")
                w = w.copy(
                    contracts = w.contracts.map { if (it.id == c.id) it.copy(status = ContractStatus.ASSIGNED, vehicleId = v.id, driverId = d.id) else it },
                    vehicles = w.vehicles.map {
                        if (it.id == v.id) it.copy(status = VehicleStatus.EN_ROUTE_TO_PICKUP, contractId = c.id, route = rt.geometry,
                            routeKm = rt.km, travelledKm = 0.0, routeProgress = 0.0, driverId = d.id) else it
                    },
                    staff = w.staff.map { if (it.id == d.id) it.copy(vehicleId = v.id) else it },
                )
                IntentOutcome(w, true, "Assigned ${c.type} to ${v.plate}")
            }
            "PURCHASE_VEHICLE" -> {
                val cls = runCatching { VehicleClass.valueOf(str("class")?.uppercase() ?: "") }.getOrNull()
                    ?: return IntentOutcome(w, false, "Unknown vehicle class")
                val price = Fleet.of(cls).price
                if (w.cash < price) return IntentOutcome(w, false, "Insufficient cash")
                val id = "veh_${w.vehicles.size + 1}_${w.tick}"
                w = w.copy(vehicles = w.vehicles + Vehicle(id, cls, plate = "CF ${w.tick.toInt() % 900 + 100}-${w.vehicles.size + 1}",
                    location = w.base, fuel = 100.0, condition = 100.0))
                tx(TxCategory.PURCHASE, -price, "Purchased ${Fleet.of(cls).label}", key = intent.key)
                IntentOutcome(w, true, "Purchased ${Fleet.of(cls).label}")
            }
            "REFUEL" -> {
                val vid = str("vehicle_id")
                val v = w.vehicles.find { it.id == vid } ?: return IntentOutcome(w, false, "Unknown vehicle")
                val cost = Fleet.of(v.vehicleClass).tank * cfg.fuelPrice + cfg.refuelFee
                if (w.cash < cost) return IntentOutcome(w, false, "Insufficient cash")
                w = w.copy(vehicles = w.vehicles.map { if (it.id == v.id) it.copy(fuel = 100.0, lowFuelTicks = 0) else it })
                tx(TxCategory.FUEL, -cost, "Refuelled ${v.plate}", key = intent.key)
                IntentOutcome(w, true, "Vehicle refuelled")
            }
            "SERVICE" -> {
                val vid = str("vehicle_id")
                val v = w.vehicles.find { it.id == vid } ?: return IntentOutcome(w, false, "Unknown vehicle")
                if (w.cash < cfg.maintenanceCost) return IntentOutcome(w, false, "Insufficient cash")
                w = w.copy(vehicles = w.vehicles.map { if (it.id == v.id) it.copy(condition = 100.0) else it })
                tx(TxCategory.REPAIR, -cfg.maintenanceCost, "Preventative service ${v.plate}", key = intent.key)
                IntentOutcome(w, true, "Service completed")
            }
            "HIRE_STAFF" -> {
                val role = runCatching { Role.valueOf(str("role")?.uppercase() ?: "") }.getOrNull()
                    ?: return IntentOutcome(w, false, "Unknown role")
                val skill = (num("skill") ?: 5.0).toInt().coerceIn(1, 10)
                val name = str("name") ?: "New Hire"
                val salary = if (role == Role.DRIVER) 135.0 else 150.0
                if (w.cash < RECRUITMENT_FEE) return IntentOutcome(w, false, "Insufficient cash for the placement fee")
                val id = "staff_${w.staff.size + 1}_${w.tick}"
                w = w.copy(staff = w.staff + Staff(id, name, role, 0.0, skill, StaffStatus.ON_SHIFT, salaryPerDay = salary))
                tx(TxCategory.SALARY, -RECRUITMENT_FEE, "Placement fee — $name", key = intent.key)
                IntentOutcome(w, true, "Hired $name as ${role.name.lowercase()}")
            }
            "ASSIGN_DRIVER" -> {
                val sid = str("staff_id")
                val s = w.staff.find { it.id == sid } ?: return IntentOutcome(w, false, "Unknown staff")
                if (s.role != Role.DRIVER) return IntentOutcome(w, false, "Only drivers can be assigned to a vehicle")
                val vid = str("vehicle_id")
                if (vid.isNullOrBlank()) {
                    w = w.copy(
                        staff = w.staff.map { if (it.id == s.id) it.copy(vehicleId = null) else it },
                        vehicles = w.vehicles.map { if (it.driverId == s.id) it.copy(driverId = null) else it },
                    )
                    return IntentOutcome(w, true, "${s.name} released from their vehicle")
                }
                val v = w.vehicles.find { it.id == vid } ?: return IntentOutcome(w, false, "Unknown vehicle")
                if (v.status != VehicleStatus.IDLE) return IntentOutcome(w, false, "Vehicle must be idle to take a driver")
                if (s.status != StaffStatus.ON_SHIFT) return IntentOutcome(w, false, "${s.name} is not on shift")
                // A driver holds one vehicle and a vehicle holds one driver; move both sides together.
                val previousDriver = v.driverId
                w = w.copy(
                    staff = w.staff.map {
                        when {
                            it.id == s.id -> it.copy(vehicleId = v.id)
                            it.vehicleId == v.id -> it.copy(vehicleId = null)
                            it.id == previousDriver -> it.copy(vehicleId = null)
                            else -> it
                        }
                    },
                    vehicles = w.vehicles.map {
                        when {
                            it.id == v.id -> it.copy(driverId = s.id)
                            it.driverId == s.id -> it.copy(driverId = null)
                            else -> it
                        }
                    },
                )
                IntentOutcome(w, true, "${s.name} assigned to ${v.plate}")
            }
            "TOGGLE_SHIFT" -> {
                val sid = str("staff_id"); val s = w.staff.find { it.id == sid } ?: return IntentOutcome(w, false, "Unknown staff")
                val next = if (s.status == StaffStatus.ON_SHIFT) StaffStatus.RESTING else StaffStatus.ON_SHIFT
                w = w.copy(staff = w.staff.map { if (it.id == s.id) it.copy(status = next) else it })
                IntentOutcome(w, true, "${s.name} is now ${next.name.lowercase()}")
            }
            "UNLOCK_ZONE" -> {
                val zid = str("zone_id"); val z = w.zones.find { it.zoneId == zid } ?: return IntentOutcome(w, false, "Unknown zone")
                if (z.unlocked) return IntentOutcome(w, true, "Zone already unlocked")
                if (w.cash < z.unlockCost) return IntentOutcome(w, false, "Insufficient cash")
                val met = w.contracts.count { it.status == ContractStatus.COMPLETED }.toDouble() /
                    max(1, w.contracts.count { it.status == ContractStatus.COMPLETED || it.status == ContractStatus.FAILED })
                if (met < z.completionThreshold && z.unlockCost > 0) return IntentOutcome(w, false, "Completion rate ${(met * 100).toInt()}% below ${(z.completionThreshold * 100).toInt()}%")
                w = w.copy(zones = w.zones.map { if (it.zoneId == zid) it.copy(unlocked = true) else it })
                tx(TxCategory.EXPANSION, -z.unlockCost, "Unlocked ${z.name}", key = intent.key)
                IntentOutcome(w, true, "Unlocked ${z.name}")
            }
            "SET_AUTO_DISPATCH" -> {
                w = w.copy(autoDispatch = p["enabled"]?.jsonPrimitive?.booleanOrNull ?: true)
                IntentOutcome(w, true, "Auto dispatch ${if (w.autoDispatch) "on" else "off"}")
            }
            "SET_PAUSED" -> {
                val paused = p["paused"]?.jsonPrimitive?.booleanOrNull ?: !w.paused
                w = w.copy(paused = paused, stepOnce = false)
                IntentOutcome(w, true, if (paused) "Simulation paused" else "Simulation resumed")
            }
            "FORCE_STEP" -> {
                w = w.copy(paused = true, stepOnce = true)
                IntentOutcome(w, true, "Advancing one tick")
            }
            "SET_SPEED" -> {
                val speed = (num("speed") ?: 1.0).coerceIn(1.0, 4.0)
                w = w.copy(speed = speed)
                IntentOutcome(w, true, "Speed set to ${speed.toInt()}x")
            }
            "RESOLVE_INCIDENT" -> resolve(w, intent, routes, cfg) { cat, amount, desc ->
                tx(cat, amount, desc, key = intent.key)
            }
            "SPAWN_CONTRACT" -> {
                val spawned = Demand.generate(w.copy(contracts = w.contracts + Contract(
                    id = "manual_${w.tick}", type = ContractType.PARCEL,
                    pickup = City.pois.first { it.zoneId == "cbd" }, dropoff = City.pois.first { it.zoneId == "waterfront" && it.poiType == PoiType.RESIDENTIAL },
                    payload = Payload(5.0), reward = 75.0, deadlineTick = w.tick + 90, createdAtTick = w.tick,
                )), w.tick, cfg)
                val extra = spawned.filter { c -> w.contracts.none { it.id == c.id } }.take(1)
                w = w.copy(contracts = w.contracts + extra)
                IntentOutcome(w, extra.isNotEmpty(), "Dispatch board refreshed")
            }
            else -> IntentOutcome(w, false, "Unknown intent ${intent.type}")
        }
    }

    private fun resolve(
        world: World, intent: Intent, routes: RouteProvider, cfg: SimConfig,
        ledger: (TxCategory, Double, String) -> Unit,
    ): IntentOutcome {
        var w = world
        val id = intent.payload["incident_id"]?.jsonPrimitive?.contentOrNull
        val rawAction = intent.payload["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "ignore"
        val action = when (rawAction) {
            "dispatch_fuel_truck" -> "fuel_truck"
            "reroute_station" -> "reroute_to_station"
            "tow_to_base" -> "tow"
            "take_loan" -> "loan"
            "pay_bonus" -> "pay_partial"
            else -> rawAction
        }
        val inc = w.incidents.find { it.id == id && !it.resolved } ?: return IntentOutcome(w, false, "Incident already resolved")
        var message = "Incident resolved"
        when (inc.type) {
            IncidentType.LOW_FUEL -> {
                val v = w.vehicles.find { it.id == inc.vehicleId }
                when (action) {
                    "fuel_truck" -> if (v != null) {
                        val cost = 120.0
                        if (w.cash < cost) return IntentOutcome(w, false, "Insufficient cash")
                        w = w.copy(vehicles = w.vehicles.map { if (it.id == v.id) it.copy(fuel = 100.0, lowFuelTicks = 0) else it })
                        ledger(TxCategory.FUEL, -cost, "Fuel truck delivered to ${v.plate}")
                        message = "Fuel truck dispatched"
                    }
                    "reroute_to_station" -> if (v != null) {
                        val station = City.fuelStation(v.location, w.zones)
                        val rt = station?.let { routes.route(v.location, it.point()) }
                        if (rt == null) return IntentOutcome(w, false, "No fuel station route available")
                        w = w.copy(vehicles = w.vehicles.map {
                            if (it.id == v.id) it.copy(status = VehicleStatus.DIVERT_TO_REFUEL, route = rt.geometry, routeKm = rt.km, travelledKm = 0.0, routeProgress = 0.0) else it
                        })
                        w = w.copy(contracts = w.contracts.map {
                            if (it.id == v.contractId) it.copy(status = ContractStatus.OPEN, vehicleId = null, driverId = null) else it
                        })
                        message = "Vehicle rerouted to ${station.label}"
                    }
                    else -> message = "Low fuel ignored — breakdown risk"
                }
            }
            IncidentType.BREAKDOWN, IncidentType.ACCIDENT -> {
                val v = w.vehicles.find { it.id == inc.vehicleId }
                when (action) {
                    "tow" -> if (v != null) {
                        if (w.cash < cfg.towCost) return IntentOutcome(w, false, "Insufficient cash")
                        w = w.copy(vehicles = w.vehicles.map { if (it.id == v.id) it.copy(status = VehicleStatus.IDLE, condition = 45.0, contractId = null, route = emptyList(), routeProgress = 0.0) else it })
                        ledger(TxCategory.TOW, -cfg.towCost, "Tow to base for ${v.plate}")
                        message = "Vehicle towed to base"
                    }
                    "field_repair" -> if (v != null) {
                        if (w.cash < cfg.fieldRepairCost) return IntentOutcome(w, false, "Insufficient cash")
                        w = w.copy(vehicles = w.vehicles.map { if (it.id == v.id) it.copy(status = VehicleStatus.IDLE, condition = 72.0, contractId = null, route = emptyList(), routeProgress = 0.0) else it })
                        ledger(TxCategory.REPAIR, -cfg.fieldRepairCost, "Field repair for ${v.plate}")
                        message = "Field repair completed"
                    }
                }
            }
            IncidentType.STRIKE -> {
                when (action) {
                    "pay_partial" -> {
                        val drivers = w.staff.filter { it.role == Role.DRIVER }
                        val partial = drivers.sumOf { it.salaryPerDay } * 0.5
                        if (w.cash < partial) return IntentOutcome(w, false, "Insufficient cash")
                        ledger(TxCategory.SALARY, -partial, "Partial payroll settlement")
                        w = w.copy(staff = w.staff.map { if (it.role == Role.DRIVER) it.copy(morale = (it.morale + 25).coerceAtMost(100.0), status = StaffStatus.ON_SHIFT, restTicks = 0) else it })
                        message = "Partial payroll paid"
                    }
                    "loan" -> {
                        w = w.copy(cash = w.cash + 5000.0, staff = w.staff.map { if (it.role == Role.DRIVER) it.copy(status = StaffStatus.ON_SHIFT, restTicks = 0, morale = (it.morale + 10).coerceAtMost(100.0)) else it })
                        ledger(TxCategory.LOAN, 5000.0, "Emergency operating loan")
                        message = "Loan drawn"
                    }
                    "fire_staff" -> {
                        val driver = w.staff.filter { it.role == Role.DRIVER }.maxByOrNull { it.salaryPerDay }
                        if (driver != null) w = w.copy(staff = w.staff.filter { it.id != driver.id })
                        message = "Driver released"
                    }
                }
            }
            IncidentType.FATIGUE -> {
                val d = w.staff.find { it.id == inc.driverId }
                if (d != null) w = w.copy(staff = w.staff.map {
                    if (it.id == d.id) it.copy(status = StaffStatus.RESTING, restTicks = cfg.forcedRestTicks, fatigue = 40.0) else it
                })
                message = "Driver sent to rest"
            }
            IncidentType.TRAFFIC_JAM -> message = "Delay accepted"
        }
        w = w.copy(incidents = w.incidents.map { if (it.id == inc.id) it.copy(resolved = true) else it })
        return IntentOutcome(w, true, message)
    }

    private inner class Run(val input: World, val routes: RouteProvider) {
        val tick = input.tick + 1
        val events = mutableListOf<Event>()
        val zones = input.zones.associateBy { it.zoneId }.toMutableMap()
        val vehicles = input.vehicles.associateBy { it.id }.toMutableMap()
        val staff = input.staff.associateBy { it.id }.toMutableMap()
        val contracts = input.contracts.associateBy { it.id }.toMutableMap()
        val incidents = input.incidents.toMutableList()
        val ledger = input.ledger.toMutableList()
        var cash = input.cash
        var completedTotal = input.lifetimeCompleted
        var failedTotal = input.lifetimeFailed
        var revenueTotal = input.lifetimeRevenue

        fun zoneOf(p: LngLat) = zones.values.firstOrNull { it.bounds.contains(p) }
        fun driverOf(v: Vehicle) = v.driverId?.let { staff[it] }
        fun contractOf(v: Vehicle) = v.contractId?.let { contracts[it] }

        fun openIncident(i: Incident) {
            if (incidents.any { !it.resolved && it.type == i.type && it.vehicleId == i.vehicleId && it.zoneId == i.zoneId }) return
            incidents += i
            events += Event("incident", i.description, i.vehicleId ?: i.zoneId ?: i.id)
        }

        fun money(cat: TxCategory, amount: Double, desc: String, contractId: String? = null) {
            cash = (cash + amount).coerceAtLeast(0.0)
            ledger += Transaction("tx_${tick}_${ledger.size}", tick, cat, round(amount * 100) / 100, contractId, desc)
        }

        fun release(vehicle: Vehicle, status: VehicleStatus = VehicleStatus.IDLE): Vehicle {
            contractOf(vehicle)?.let { c ->
                if (c.status == ContractStatus.ASSIGNED || c.status == ContractStatus.IN_PROGRESS) {
                    contracts[c.id] = c.copy(status = ContractStatus.OPEN, vehicleId = null, driverId = null)
                }
            }
            return vehicle.copy(status = status, contractId = null, route = emptyList(), routeKm = 0.0, travelledKm = 0.0, routeProgress = 0.0)
        }

        fun run(): TickResult {
            restDrivers()
            for (id in vehicles.keys.toList()) vehicles[id] = vehicles[id]!!.let { tickVehicle(it) }
            applyFatigue()
            for (v in vehicles.values.toList()) checkVehicleIncidents(v)
            checkZoneTraffic()
            failExpired()
            payroll()
            val generated = Demand.generate(snapshot(includeLedger = false), tick, cfg)
            generated.forEach { contracts[it.id] = it }
            if (generated.isNotEmpty()) events += Event("demand", "${generated.size} new contracts")
            if (input.autoDispatch) dispatch()
            val world = snapshot(includeLedger = true)
            return TickResult(world, events)
        }

        fun snapshot(includeLedger: Boolean): World {
            val active = contracts.values.filter { it.status in setOf(ContractStatus.OPEN, ContractStatus.ASSIGNED, ContractStatus.IN_PROGRESS) }
            val closed = contracts.values.filter { it.status in setOf(ContractStatus.COMPLETED, ContractStatus.FAILED) }
                .sortedBy { it.completedAtTick ?: it.deadlineTick }.takeLast(200)
            val openIncidents = incidents.filter { !it.resolved }
            val resolvedIncidents = incidents.filter { it.resolved }.takeLast(40)
            return input.copy(
                tick = tick, cash = round(cash * 100) / 100, lastPayrollTick = lastPayroll,
                paused = input.paused, stepOnce = false,
                zones = zones.values.toList(), staff = staff.values.toList(), vehicles = vehicles.values.toList(),
                contracts = active + closed, incidents = openIncidents + resolvedIncidents,
                ledger = if (includeLedger) ledger.takeLast(400) else input.ledger,
                lifetimeCompleted = completedTotal, lifetimeFailed = failedTotal, lifetimeRevenue = revenueTotal,
            )
        }

        fun restDrivers() {
            for ((id, s) in staff.toList()) {
                if (s.status == StaffStatus.RESTING && s.role == Role.DRIVER) {
                    val left = (s.restTicks - 1).coerceAtLeast(0)
                    staff[id] = s.copy(fatigue = (s.fatigue - cfg.restRecovery).coerceIn(0.0, 100.0), restTicks = left,
                        status = if (left == 0 && s.morale >= 20) StaffStatus.ON_SHIFT else StaffStatus.RESTING)
                } else if (s.role == Role.DRIVER && s.status == StaffStatus.ON_SHIFT && s.fatigue > cfg.fatigueRest) {
                    val v = s.vehicleId?.let { vehicles[it] }
                    if (v != null) vehicles[v.id] = release(v, VehicleStatus.FORCED_REST)
                    staff[id] = s.copy(status = StaffStatus.RESTING, restTicks = cfg.forcedRestTicks)
                    openIncident(Incident("inc_${tick}_fatigue_$id", vehicleId = v?.id, driverId = id, type = IncidentType.FATIGUE,
                        severity = 2, costIfIgnored = 0.0, description = "${s.name} is exhausted and must rest", createdTick = tick))
                    events += Event("fatigue", "${s.name} forced to rest")
                }
            }
        }

        fun applyFatigue() {
            for ((id, s) in staff.toList()) {
                if (s.status != StaffStatus.ON_SHIFT || s.role != Role.DRIVER) continue
                val driving = s.vehicleId?.let { vehicles[it] }?.status in setOf(
                    VehicleStatus.EN_ROUTE_TO_PICKUP, VehicleStatus.EN_ROUTE_TO_DROPOFF, VehicleStatus.DIVERT_TO_REFUEL,
                )
                staff[id] = s.copy(fatigue = (s.fatigue + cfg.fatiguePerTick * if (driving) 1.0 else 0.15).coerceIn(0.0, 100.0))
            }
        }

        fun tickVehicle(v: Vehicle): Vehicle {
            val c = contractOf(v)
            val d = driverOf(v)
            return when (v.status) {
                VehicleStatus.IDLE, VehicleStatus.BREAKDOWN, VehicleStatus.MAINTENANCE, VehicleStatus.FORCED_REST -> v
                VehicleStatus.AT_PICKUP -> {
                    if (v.loadingTicks > 1) v.copy(loadingTicks = v.loadingTicks - 1)
                    else if (c == null) release(v)
                    else {
                        val loading = when (c.type) {
                            ContractType.RIDESHARE -> 0
                            ContractType.FOOD, ContractType.PARCEL -> 2
                            ContractType.CARGO -> 3
                        }
                        if (loading == 0) startDropoff(v, c) else v.copy(status = VehicleStatus.LOADING, loadingTicks = loading)
                    }
                }
                VehicleStatus.LOADING -> if (v.loadingTicks > 1) v.copy(loadingTicks = v.loadingTicks - 1)
                    else if (c != null) startDropoff(v, c) else release(v)
                VehicleStatus.UNLOADING -> if (v.loadingTicks > 1) v.copy(loadingTicks = v.loadingTicks - 1) else complete(v, c)
                else -> move(v, c, d)
            }
        }

        fun move(v: Vehicle, c: Contract?, d: Staff?): Vehicle {
            if (v.route.isEmpty()) return release(v)
            val traffic = zoneOf(v.location)?.trafficIndex ?: 0.25
            val spec = Fleet.of(v.vehicleClass)
            val km = spec.speed * (1 - 0.5 * traffic) / 60.0
            val burn = spec.burn * (1 + (c?.payload?.weightKg ?: 0.0) * 0.0005) * (1 + traffic * 0.1) / max(1, d?.skill ?: 5)
            val travelled = v.travelledKm + km
            val pos = Geo.at(v.route, travelled)
            var next = v.copy(
                fuel = (v.fuel - burn).coerceAtLeast(0.0),
                mileageKm = v.mileageKm + km,
                condition = (v.condition - km * spec.wearPerKm).coerceIn(0.0, 100.0),
                location = pos.point, travelledKm = travelled, routeProgress = pos.progress, heading = pos.heading,
            )
            next = if (next.fuel <= 0.0) next.copy(fuel = 0.0) else next
            if (!pos.arrived) return next
            return when (v.status) {
                VehicleStatus.EN_ROUTE_TO_PICKUP -> {
                    if (c != null) contracts[c.id]?.let { contracts[it.id] = it.copy(status = ContractStatus.IN_PROGRESS) }
                    next.copy(status = VehicleStatus.AT_PICKUP, loadingTicks = 1, routeProgress = 1.0)
                }
                VehicleStatus.EN_ROUTE_TO_DROPOFF -> next.copy(status = VehicleStatus.UNLOADING, loadingTicks = 1)
                VehicleStatus.DIVERT_TO_REFUEL -> {
                    val cost = spec.tank * cfg.fuelPrice + cfg.refuelFee
                    money(TxCategory.FUEL, -cost, "Refuelled ${v.plate} at station")
                    release(next.copy(fuel = 100.0, lowFuelTicks = 0))
                }
                else -> next
            }
        }

        fun startDropoff(v: Vehicle, c: Contract): Vehicle {
            val rt = routes.route(v.location, c.dropoff.point()) ?: run {
                events += Event("routing", "OSRM unavailable for contract ${c.id}")
                return v.copy(status = VehicleStatus.AT_PICKUP)
            }
            contracts[c.id] = c.copy(status = ContractStatus.IN_PROGRESS, routeKm = rt.km)
            return v.copy(status = VehicleStatus.EN_ROUTE_TO_DROPOFF, route = rt.geometry, routeKm = rt.km,
                travelledKm = 0.0, routeProgress = 0.0)
        }

        fun complete(v: Vehicle, c: Contract?): Vehicle {
            if (c == null) return release(v)
            money(TxCategory.REVENUE, c.reward, "Completed ${c.type.name.lowercase()} ${c.id}", c.id)
            completedTotal++
            revenueTotal += c.reward
            contracts[c.id] = c.copy(status = ContractStatus.COMPLETED, completedAtTick = tick)
            zones[c.zoneId]?.let { zones[c.zoneId] = it.copy(completed = it.completed + 1) }
            events += Event("completed", "Contract ${c.id} delivered", c.id, c.reward)
            val d = v.driverId?.let { staff[it] }
            if (d != null) staff[d.id] = d.copy(status = StaffStatus.ON_SHIFT, vehicleId = v.id)
            return v.copy(status = VehicleStatus.IDLE, contractId = null, route = emptyList(), routeKm = 0.0,
                travelledKm = 0.0, routeProgress = 0.0, loadingTicks = 0)
        }

        fun checkVehicleIncidents(v: Vehicle) {
            if (v.fuel < cfg.lowFuel && v.status != VehicleStatus.BREAKDOWN) {
                vehicles[v.id] = v.copy(lowFuelTicks = v.lowFuelTicks + 1)
                openIncident(Incident("inc_${tick}_fuel_${v.id}", vehicleId = v.id, driverId = v.driverId, contractId = v.contractId,
                    type = IncidentType.LOW_FUEL, severity = 2, costIfIgnored = cfg.towCost,
                    description = "${v.plate} is low on fuel (${v.fuel.toInt()}%)", createdTick = tick))
                if (v.lowFuelTicks + 1 >= cfg.lowFuelBreakdownTicks) {
                    vehicles[v.id] = release(v, VehicleStatus.BREAKDOWN).copy(lowFuelTicks = 0)
                    money(TxCategory.TOW, -cfg.towCost, "Emergency tow ${v.plate}")
                    openIncident(Incident("inc_${tick}_break_${v.id}", vehicleId = v.id, type = IncidentType.BREAKDOWN, severity = 3,
                        costIfIgnored = cfg.fieldRepairCost, description = "${v.plate} ran dry and broke down", createdTick = tick))
                    events += Event("breakdown", "${v.plate} broke down", v.id)
                }
            }
            if (v.condition < cfg.conditionBreakdown && v.status != VehicleStatus.BREAKDOWN) {
                vehicles[v.id] = release(v, VehicleStatus.BREAKDOWN)
                openIncident(Incident("inc_${tick}_cond_${v.id}", vehicleId = v.id, type = IncidentType.BREAKDOWN, severity = 3,
                    costIfIgnored = cfg.fieldRepairCost, description = "${v.plate} condition critical (${v.condition.toInt()}%)", createdTick = tick))
            }
        }

        fun checkZoneTraffic() {
            for (z in zones.values) {
                if (z.trafficIndex > cfg.trafficJam && z.unlocked) {
                    openIncident(Incident("inc_${tick}_jam_${z.zoneId}", zoneId = z.zoneId, type = IncidentType.TRAFFIC_JAM,
                        severity = 1, description = "Gridlock in ${z.name} (${(z.trafficIndex * 100).toInt()}%)", createdTick = tick))
                }
            }
        }

        fun failExpired() {
            for (c in contracts.values.toList()) {
                if (c.status in setOf(ContractStatus.COMPLETED, ContractStatus.FAILED) || tick <= c.deadlineTick) continue
                contracts[c.id] = c.copy(status = ContractStatus.FAILED)
                failedTotal++
                zones[c.zoneId]?.let { zones[c.zoneId] = it.copy(failed = it.failed + 1, unmet = it.unmet + 1) }
                c.vehicleId?.let { vid -> vehicles[vid]?.let { vehicles[vid] = release(it) } }
                events += Event("failed", "Contract ${c.id} missed its deadline", c.id)
            }
        }

        fun payroll() {
            if (tick - input.lastPayrollTick < cfg.payrollTicks) return
            val due = staff.values.filter { it.status != StaffStatus.FIRED }.sumOf { it.salaryPerDay }
            if (cash >= due) {
                money(TxCategory.SALARY, -due, "Payroll ($tick)")
            } else {
                cash = 0.0
                staff.keys.toList().forEach { id -> staff[id]?.let { staff[id] = it.copy(morale = (it.morale - 30).coerceAtLeast(0.0)) } }
                for ((id, s) in staff.toList()) {
                    if (s.role == Role.DRIVER) staff[id] = s.copy(status = StaffStatus.RESTING, restTicks = cfg.forcedRestTicks)
                }
                openIncident(Incident("inc_${tick}_strike", type = IncidentType.STRIKE, severity = 3,
                    costIfIgnored = due, description = "Payroll of R${due.toInt()} cannot be met — drivers striking", createdTick = tick))
                events += Event("strike", "Payroll shortfall: drivers on strike")
            }
            val updated = input.lastPayrollTick + cfg.payrollTicks
            lastPayroll = updated
        }

        var lastPayroll: Long = input.lastPayrollTick

        fun dispatch() {
            val open = contracts.values.filter { it.status == ContractStatus.OPEN }.sortedWith(compareBy({ it.createdAtTick }, { it.id }))
            for (c in open) {
                val candidates = mutableListOf<Matching.Candidate>()
                for (v in vehicles.values) {
                    if (v.status != VehicleStatus.IDLE) continue
                    val d = driverOf(v) ?: continue
                    if (d.status != StaffStatus.ON_SHIFT || d.fatigue > cfg.fatigueRest) continue
                    val rt = routes.route(v.location, c.pickup.point()) ?: continue
                    if (rt.km > cfg.searchRadiusKm) continue
                    val tripKm = Geo.km(c.pickup.point(), c.dropoff.point()) * 1.35
                    val pickupZone = zoneOf(c.pickup.point())?.trafficIndex ?: 0.3
                    val vehicleZone = zoneOf(v.location)?.trafficIndex ?: 0.3
                    candidates += Matching.Candidate(c, v, d, rt.km, tripKm, pickupZone, vehicleZone)
                }
                val chosen = Matching.choose(candidates, cfg) ?: continue
                val cand = chosen.first
                val rt = routes.route(cand.vehicle.location, c.pickup.point()) ?: continue
                contracts[c.id] = c.copy(status = ContractStatus.ASSIGNED, vehicleId = cand.vehicle.id, driverId = cand.driver.id)
                vehicles[cand.vehicle.id] = cand.vehicle.copy(status = VehicleStatus.EN_ROUTE_TO_PICKUP, contractId = c.id,
                    route = rt.geometry, routeKm = rt.km, travelledKm = 0.0, routeProgress = 0.0)
                staff[cand.driver.id] = cand.driver.copy(vehicleId = cand.vehicle.id, status = StaffStatus.ON_SHIFT)
                events += Event("assigned", "${cand.vehicle.plate} dispatched to ${c.pickup.label}")
            }
        }
    }
}

data class ExpansionRequest(
    val zoneId: String? = null, val additionalVehicles: Int = 0, val additionalDrivers: Int = 0,
    val marketingSpend: Double = 0.0, val days: Int = 30, val iterations: Int = 500,
)
data class ExpansionResult(
    val p10: Double, val p50: Double, val p90: Double, val breakEvenDayP50: Int?,
    val insolvencyProbability: Double, val expectedDailyRevenue: Double, val expectedDailyCost: Double,
    val iterations: Int,
)

/** Reuses the same demand and cost curves as the live engine. Read-only: never mutates a world. */
object MonteCarlo {
    fun run(world: World, req: ExpansionRequest, cfg: SimConfig = SimConfig()): ExpansionResult {
        val iterations = req.iterations.coerceIn(1, 2000)
        val days = req.days.coerceIn(1, 365)
        val unlockedDemand = world.zones.filter { it.unlocked }.sumOf { it.demandMultiplier } +
            if (req.zoneId != null) (world.zones.find { it.zoneId == req.zoneId }?.demandMultiplier ?: 0.0) else 0.0
        val salaries = world.staff.sumOf { it.salaryPerDay } + req.additionalDrivers * 135.0
        val classes = world.vehicles.map { it.vehicleClass } + List(req.additionalVehicles) { VehicleClass.VAN }
        val dailyCost = Economy.dailyCost(salaries, classes) + req.marketingSpend / days
        val avgReward = listOf(
            Economy.reward(ContractType.RIDESHARE, 7.0, 75.0, 1.0),
            Economy.reward(ContractType.FOOD, 7.0, 12.0, 1.0),
            Economy.reward(ContractType.PARCEL, 8.0, 20.0, 1.0),
            Economy.reward(ContractType.CARGO, 12.0, 900.0, 1.0),
        ).average()
        val marketingBoost = 1 + (req.marketingSpend / 25000.0)
        val expectedDemandPerDay = unlockedDemand * cfg.demandPerTickPerZone * cfg.payrollTicks * marketingBoost
        val capacityPerDay = Economy.capacity(classes.size)
        val outcomes = DoubleArray(iterations)
        var insolvent = 0
        var breakEvenSum = 0
        var breakEvenCount = 0
        val seedBase = world.seed xor world.tick
        for (i in 0 until iterations) {
            val rng = Rng(seedBase + i * 7919L)
            var cash = world.cash
            var broke = false
            var breakEven: Int? = null
            var totalRevenue = 0.0
            for (day in 1..days) {
                val demand = rng.poisson(expectedDemandPerDay)
                val served = minOf(demand, capacityPerDay.toInt())
                val revenue = served * avgReward * rng.range(0.85, 1.15)
                cash += revenue - dailyCost
                totalRevenue += revenue
                if (revenue >= dailyCost && breakEven == null) breakEven = day
                if (cash < 0) { broke = true; cash = 0.0 }
            }
            outcomes[i] = cash
            if (broke) insolvent++
            if (breakEven != null) { breakEvenSum += breakEven!!; breakEvenCount++ }
        }
        outcomes.sort()
        fun pct(p: Double) = outcomes[((outcomes.size - 1) * p).toInt().coerceIn(0, outcomes.size - 1)]
        return ExpansionResult(
            p10 = round(pct(0.10) * 100) / 100, p50 = round(pct(0.50) * 100) / 100, p90 = round(pct(0.90) * 100) / 100,
            breakEvenDayP50 = if (breakEvenCount > 0) breakEvenSum / breakEvenCount else null,
            insolvencyProbability = round(insolvent.toDouble() / iterations * 1000) / 1000,
            expectedDailyRevenue = round(expectedDemandPerDay.coerceAtMost(capacityPerDay) * avgReward * 100) / 100,
            expectedDailyCost = round(dailyCost * 100) / 100, iterations = iterations,
        )
    }
}
