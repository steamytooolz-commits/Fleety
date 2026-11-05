package com.cityflow.server

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable data class LngLat(val lng: Double, val lat: Double)
@Serializable data class Bounds(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double) {
    fun contains(p: LngLat) = p.lat in minLat..maxLat && p.lng in minLng..maxLng
}
@Serializable enum class Role { DRIVER, DISPATCHER, MECHANIC, MANAGER }
@Serializable enum class StaffStatus { IDLE, ON_SHIFT, RESTING, FIRED }
@Serializable data class Staff(
    val id: String, val name: String, val role: Role, val fatigue: Double = 0.0, val skill: Int = 5,
    val status: StaffStatus = StaffStatus.IDLE, val vehicleId: String? = null,
    val salaryPerDay: Double = 120.0, val restTicks: Int = 0, val morale: Double = 100.0,
)
@Serializable enum class VehicleClass { SEDAN, VAN, TRUCK, REFRIGERATED }
@Serializable enum class VehicleStatus {
    IDLE, EN_ROUTE_TO_PICKUP, AT_PICKUP, LOADING, EN_ROUTE_TO_DROPOFF, UNLOADING,
    DIVERT_TO_REFUEL, BREAKDOWN, FORCED_REST, MAINTENANCE,
}
@Serializable data class Vehicle(
    val id: String, val vehicleClass: VehicleClass, val plate: String,
    val fuel: Double = 100.0, val condition: Double = 100.0, val mileageKm: Double = 0.0,
    val status: VehicleStatus = VehicleStatus.IDLE, val location: LngLat,
    val route: List<LngLat> = emptyList(), val routeKm: Double = 0.0, val travelledKm: Double = 0.0,
    val routeProgress: Double = 0.0, val driverId: String? = null, val contractId: String? = null,
    val loadingTicks: Int = 0, val lowFuelTicks: Int = 0, val heading: Double = 0.0,
)
@Serializable enum class ContractType { RIDESHARE, FOOD, PARCEL, CARGO }
@Serializable enum class ContractStatus { OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED }
@Serializable enum class PoiType { RESTAURANT, RESIDENTIAL, TRANSIT_HUB, COMMERCIAL, WAREHOUSE, INDUSTRIAL, FUEL_STATION }
@Serializable data class Place(val lat: Double, val lng: Double, val label: String, val zoneId: String, val poiType: PoiType) {
    fun point() = LngLat(lng, lat)
}
@Serializable data class Payload(val weightKg: Double = 0.0, val requiresRefrigeration: Boolean = false, val description: String = "Goods")
@Serializable data class Contract(
    val id: String, val type: ContractType, val pickup: Place, val dropoff: Place, val payload: Payload,
    val reward: Double, val deadlineTick: Long, val status: ContractStatus = ContractStatus.OPEN,
    val vehicleId: String? = null, val driverId: String? = null, val createdAtTick: Long = 0,
    val completedAtTick: Long? = null, val routeKm: Double = 0.0, val zoneId: String = pickup.zoneId,
)
@Serializable enum class IncidentType { LOW_FUEL, BREAKDOWN, ACCIDENT, STRIKE, TRAFFIC_JAM, FATIGUE }
@Serializable data class Incident(
    val id: String, val vehicleId: String? = null, val driverId: String? = null, val contractId: String? = null,
    val zoneId: String? = null, val type: IncidentType, val severity: Int, val resolved: Boolean = false,
    val costIfIgnored: Double = 0.0, val description: String = "", val createdTick: Long = 0,
)
@Serializable enum class TxCategory { REVENUE, FUEL, SALARY, REPAIR, TOW, EXPANSION, PURCHASE, PENALTY, LOAN }
@Serializable data class Transaction(
    val id: String, val tick: Long, val category: TxCategory, val amount: Double,
    val contractId: String? = null, val description: String = "", val idempotencyKey: String? = null,
)
@Serializable data class Zone(
    val zoneId: String, val name: String, val bounds: Bounds, val demandMultiplier: Double = 1.0,
    val trafficIndex: Double = 0.3, val unlocked: Boolean = false, val unlockCost: Double = 0.0,
    val completionThreshold: Double = 0.75, val completed: Int = 0, val failed: Int = 0, val unmet: Int = 0,
)
@Serializable data class World(
    val playerId: String, val companyName: String, val base: LngLat, val cash: Double,
    val level: Int = 1, val tick: Long = 0, val seed: Long = 1337,
    val zones: List<Zone>, val staff: List<Staff>, val vehicles: List<Vehicle>,
    val contracts: List<Contract>, val incidents: List<Incident>, val ledger: List<Transaction>,
    val lastPayrollTick: Long = 0, val autoDispatch: Boolean = true,
    val paused: Boolean = false, val stepOnce: Boolean = false, val speed: Double = 1.0,
)
data class Event(val type: String, val description: String, val entityId: String? = null, val amount: Double = 0.0)
data class TickResult(val world: World, val events: List<Event>)

data class SimConfig(
    val searchRadiusKm: Double = 12.0, val minScore: Double = 0.35, val maxOpenContracts: Int = 20,
    val demandPerTickPerZone: Double = 0.08, val payrollTicks: Long = 30,
    val lowFuel: Double = 15.0, val lowFuelBreakdownTicks: Int = 3, val conditionBreakdown: Double = 20.0,
    val fatigueRest: Double = 80.0, val fatiguePerTick: Double = 0.45, val restRecovery: Double = 0.8,
    val forcedRestTicks: Int = 60, val fuelPrice: Double = 1.15, val refuelFee: Double = 25.0,
    val maintenanceCost: Double = 220.0, val towCost: Double = 350.0, val fieldRepairCost: Double = 520.0,
    val trafficJam: Double = 0.8,
)

data class VehicleSpec(val label: String, val tank: Double, val burn: Double, val capacity: Double, val upkeep: Double, val price: Double, val speed: Double, val wearPerKm: Double)
object Fleet {
    fun of(c: VehicleClass): VehicleSpec = when (c) {
        VehicleClass.SEDAN -> VehicleSpec("Sedan Cruiser", 50.0, 0.35, 180.0, 45.0, 8500.0, 45.0, 0.0022)
        VehicleClass.VAN -> VehicleSpec("Express Van", 70.0, 0.50, 850.0, 65.0, 14000.0, 42.0, 0.0030)
        VehicleClass.TRUCK -> VehicleSpec("Heavy Cargo Truck", 120.0, 0.90, 3500.0, 110.0, 26000.0, 36.0, 0.0045)
        VehicleClass.REFRIGERATED -> VehicleSpec("ChillVan Fresh", 80.0, 0.65, 1200.0, 90.0, 21000.0, 40.0, 0.0034)
    }
}

object Geo {
    fun km(a: LngLat, b: LngLat): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1; val dl = Math.toRadians(b.lng - a.lng)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * 6371.0088 * atan2(sqrt(h), sqrt(1 - h))
    }
    fun total(route: List<LngLat>): Double = (1 until route.size).sumOf { km(route[it - 1], route[it]) }
    fun cumulative(route: List<LngLat>): DoubleArray {
        val out = DoubleArray(route.size)
        for (i in 1 until route.size) out[i] = out[i - 1] + km(route[i - 1], route[i])
        return out
    }
    fun bearing(a: LngLat, b: LngLat): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat); val dl = Math.toRadians(b.lng - a.lng)
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
    data class Pos(val point: LngLat, val progress: Double, val arrived: Boolean, val heading: Double)
    /** Position strictly along the polyline; never interpolates between unrelated endpoints. */
    fun at(route: List<LngLat>, travelled: Double): Pos {
        if (route.isEmpty()) return Pos(LngLat(0.0, 0.0), 0.0, true, 0.0)
        if (route.size == 1) return Pos(route[0], 1.0, true, 0.0)
        val cum = cumulative(route); val total = cum.last()
        if (total <= 0.0) return Pos(route.last(), 1.0, true, bearing(route[0], route.last()))
        if (travelled <= 0.0) return Pos(route[0], 0.0, false, bearing(route[0], route[1]))
        if (travelled >= total) return Pos(route.last(), 1.0, true, bearing(route[route.size - 2], route.last()))
        var i = 1
        while (i < route.size && cum[i] < travelled) i++
        val from = route[i - 1]; val to = route[i]
        val seg = (cum[i] - cum[i - 1]).coerceAtLeast(1e-9)
        val f = ((travelled - cum[i - 1]) / seg).coerceIn(0.0, 1.0)
        return Pos(
            LngLat(from.lng + f * (to.lng - from.lng), from.lat + f * (to.lat - from.lat)),
            (travelled / total).coerceIn(0.0, 1.0), false, bearing(from, to),
        )
    }
    fun distanceToRoute(p: LngLat, route: List<LngLat>): Double {
        if (route.size < 2) return if (route.isEmpty()) Double.MAX_VALUE else km(p, route[0])
        var best = Double.MAX_VALUE
        for (i in 1 until route.size) {
            val a = route[i - 1]; val b = route[i]
            val kLat = 111.0; val kLng = 111.0 * cos(Math.toRadians(p.lat))
            val bx = (b.lng - a.lng) * kLng; val by = (b.lat - a.lat) * kLat
            val px = (p.lng - a.lng) * kLng; val py = (p.lat - a.lat) * kLat
            val len = bx * bx + by * by
            val t = if (len <= 1e-12) 0.0 else ((px * bx + py * by) / len).coerceIn(0.0, 1.0)
            best = min(best, sqrt((px - t * bx).pow(2) + (py - t * by).pow(2)))
        }
        return best
    }
}

/** SplitMix64, seeded per (world seed, tick, zone). Demand only: never dispatch. */
class Rng(seed: Long) {
    private var s = seed
    fun nextLong(): Long {
        s += -0x61c8864680b583ebL
        var z = s
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53).toDouble())
    fun nextInt(n: Int): Int = if (n <= 0) 0 else (nextDouble() * n).toInt().coerceIn(0, n - 1)
    fun range(a: Double, b: Double) = a + nextDouble() * (b - a)
    fun poisson(lambda: Double): Int {
        if (lambda <= 0.0) return 0
        val limit = exp(-lambda); var k = 0; var p = nextDouble()
        while (p > limit && k < 10000) { k++; p *= nextDouble() }
        return k
    }
    companion object {
        fun seed(worldSeed: Long, tick: Long, key: String): Long {
            var h = worldSeed xor (tick * -0x61c8864680b583ebL)
            for (c in key) h = (h xor c.code.toLong()) * 0x100000001b3L
            return h
        }
    }
}

object City {
    val base = LngLat(18.4241, -33.9249)
    private data class Z(val id: String, val name: String, val b: Bounds, val demand: Double, val traffic: Double, val cost: Double, val threshold: Double, val open: Boolean)
    private val zoneDefs = listOf(
        Z("cbd", "Cape Town CBD", Bounds(-33.9330, 18.4120, -33.9130, 18.4360), 1.30, 0.55, 0.0, 0.75, true),
        Z("waterfront", "V&A Waterfront", Bounds(-33.9130, 18.4080, -33.8990, 18.4300), 1.10, 0.40, 0.0, 0.75, true),
        Z("woodstock", "Woodstock & Salt River", Bounds(-33.9340, 18.4320, -33.9200, 18.4520), 0.85, 0.35, 6500.0, 0.75, false),
        Z("observatory", "Observatory", Bounds(-33.9430, 18.4600, -33.9260, 18.4800), 0.70, 0.30, 9000.0, 0.70, false),
        Z("seapoint", "Sea Point", Bounds(-33.9250, 18.3740, -33.9060, 18.3940), 0.80, 0.25, 12000.0, 0.70, false),
    )
    val pois = listOf(
        Place(-33.9225, 18.4240, "Adderley Street Transit Hub", "cbd", PoiType.TRANSIT_HUB),
        Place(-33.9210, 18.4180, "Long Street Kitchen", "cbd", PoiType.RESTAURANT),
        Place(-33.9195, 18.4195, "Bree Street Bistro", "cbd", PoiType.RESTAURANT),
        Place(-33.9160, 18.4300, "Foreshore Apartments", "cbd", PoiType.RESIDENTIAL),
        Place(-33.9310, 18.4170, "Gardens Terraces", "cbd", PoiType.RESIDENTIAL),
        Place(-33.9180, 18.4170, "Cape Quarter Mall", "cbd", PoiType.COMMERCIAL),
        Place(-33.9155, 18.4160, "De Waterkant Depot", "cbd", PoiType.WAREHOUSE),
        Place(-33.9200, 18.4280, "CBD Fuel Stop", "cbd", PoiType.FUEL_STATION),
        Place(-33.9046, 18.4180, "V&A Mall", "waterfront", PoiType.COMMERCIAL),
        Place(-33.9070, 18.4200, "Clock Tower Precinct", "waterfront", PoiType.TRANSIT_HUB),
        Place(-33.9100, 18.4150, "Marina Residential", "waterfront", PoiType.RESIDENTIAL),
        Place(-33.9060, 18.4190, "Quay Four Restaurant", "waterfront", PoiType.RESTAURANT),
        Place(-33.9030, 18.4230, "Waterfront Depot", "waterfront", PoiType.WAREHOUSE),
        Place(-33.9080, 18.4130, "Waterfront Fuel", "waterfront", PoiType.FUEL_STATION),
        Place(-33.9275, 18.4380, "Old Biscuit Mill", "woodstock", PoiType.COMMERCIAL),
        Place(-33.9290, 18.4400, "Woodstock Bakery", "woodstock", PoiType.RESTAURANT),
        Place(-33.9310, 18.4430, "Salt River Depot", "woodstock", PoiType.WAREHOUSE),
        Place(-33.9280, 18.4340, "Albert Road Apartments", "woodstock", PoiType.RESIDENTIAL),
        Place(-33.9265, 18.4390, "Woodstock Exchange", "woodstock", PoiType.COMMERCIAL),
        Place(-33.9300, 18.4370, "Woodstock Fuel", "woodstock", PoiType.FUEL_STATION),
        Place(-33.9360, 18.4700, "Observatory Station", "observatory", PoiType.TRANSIT_HUB),
        Place(-33.9345, 18.4660, "Lower Main Kitchen", "observatory", PoiType.RESTAURANT),
        Place(-33.9380, 18.4720, "Obs Cottages", "observatory", PoiType.RESIDENTIAL),
        Place(-33.9330, 18.4680, "River Club Flats", "observatory", PoiType.RESIDENTIAL),
        Place(-33.9160, 18.3850, "Sea Point Promenade", "seapoint", PoiType.COMMERCIAL),
        Place(-33.9180, 18.3830, "Regent Road Grill", "seapoint", PoiType.RESTAURANT),
        Place(-33.9210, 18.3800, "Bantry Bay Apartments", "seapoint", PoiType.RESIDENTIAL),
        Place(-33.9140, 18.3880, "Sea Point Garage", "seapoint", PoiType.FUEL_STATION),
    )
    fun zones() = zoneDefs.map {
        Zone(it.id, it.name, it.b, it.demand, it.traffic, it.open, it.cost, it.threshold)
    }
    fun poisIn(zoneId: String, type: PoiType? = null) = pois.filter { it.zoneId == zoneId && (type == null || it.poiType == type) }
    fun fuelStation(p: LngLat, zones: List<Zone>): Place? =
        pois.filter { it.poiType == PoiType.FUEL_STATION && it.zoneId in zones.filter { z -> z.unlocked }.map { z -> z.zoneId } }
            .minByOrNull { Geo.km(p, it.point()) }
    fun initialWorld(playerId: String, company: String = "Apex City Logistics", seed: Long = 1337, cash: Double = 18500.0) = World(
        playerId = playerId, companyName = company, base = base, cash = cash, seed = seed, zones = zones(),
        staff = listOf(
            Staff("staff_drv_01", "Sipho Khumalo", Role.DRIVER, 12.0, 8, StaffStatus.ON_SHIFT, "veh_01", 135.0),
            Staff("staff_dsp_01", "David Nkosi", Role.DISPATCHER, 5.0, 7, StaffStatus.ON_SHIFT, null, 150.0),
            Staff("staff_mec_01", "Marcus van der Merwe", Role.MECHANIC, 0.0, 9, StaffStatus.ON_SHIFT, null, 160.0),
        ),
        vehicles = listOf(
            Vehicle("veh_01", VehicleClass.VAN, "CA 492-811", 88.0, 94.0, 1420.0, VehicleStatus.IDLE, base, driverId = "staff_drv_01"),
            Vehicle("veh_02", VehicleClass.SEDAN, "CA 631-904", 92.0, 98.0, 750.0, VehicleStatus.IDLE, LngLat(18.4240, -33.9225)),
        ),
        contracts = emptyList(), incidents = emptyList(),
        ledger = listOf(Transaction("tx_seed_capital", 0, TxCategory.REVENUE, cash, description = "Initial logistics venture capital")),
    )
}

object Economy {
    fun reward(type: ContractType, km: Double, weight: Double, demand: Double): Double {
        val base = when (type) { ContractType.RIDESHARE -> 45.0; ContractType.FOOD -> 60.0; ContractType.PARCEL -> 55.0; ContractType.CARGO -> 180.0 }
        val perKm = when (type) { ContractType.RIDESHARE -> 6.5; ContractType.FOOD -> 7.5; ContractType.PARCEL -> 6.0; ContractType.CARGO -> 9.0 }
        val weightPart = if (type == ContractType.CARGO) weight * 0.04 else 0.0
        return round((base + km * perKm + weightPart) * (1 + (demand - 1) * 0.2) * 100) / 100
    }
    fun fuelPctFor(cls: VehicleClass, km: Double): Double {
        val s = Fleet.of(cls); return km / (s.speed / 60.0) * s.burn
    }
    fun dailyCost(salaries: Double, classes: List<VehicleClass>): Double =
        salaries + classes.sumOf { Fleet.of(it).upkeep } + classes.sumOf { fuelPctFor(it, 45.0) * 1.15 }
    fun capacity(vehicles: Int): Double = vehicles * 5.5
}

object Matching {
    data class Candidate(val contract: Contract, val vehicle: Vehicle, val driver: Staff,
                         val pickupKm: Double, val tripKm: Double, val pickupTraffic: Double, val vehicleTraffic: Double)
    data class Score(val vehicleId: String, val total: Double)
    fun suitability(type: ContractType, p: Payload, cls: VehicleClass): Double? {
        val spec = Fleet.of(cls)
        if (p.weightKg > spec.capacity) return null
        if (p.requiresRefrigeration && cls != VehicleClass.REFRIGERATED) return null
        return when (type) {
            ContractType.CARGO -> when {
                p.weightKg > 500 && cls != VehicleClass.TRUCK -> null
                cls == VehicleClass.TRUCK -> 1.0
                else -> 0.6
            }
            ContractType.FOOD -> if (cls == VehicleClass.SEDAN) 0.55 else 1.0
            ContractType.RIDESHARE -> if (cls == VehicleClass.TRUCK) 0.4 else 1.0
            ContractType.PARCEL -> if (cls == VehicleClass.TRUCK) 0.7 else 1.0
        }
    }
    fun score(c: Candidate, cfg: SimConfig): Score? {
        val suit = suitability(c.contract.type, c.contract.payload, c.vehicle.vehicleClass) ?: return null
        val prox = (1 - c.pickupKm / cfg.searchRadiusKm).coerceIn(0.0, 1.0)
        val eta = (1 - (c.pickupKm / Fleet.of(c.vehicle.vehicleClass).speed * 60) / 60).coerceIn(0.0, 1.0)
        val skill = ((c.driver.skill - 1) / 9.0).coerceIn(0.0, 1.0)
        val need = max(0.5, Economy.fuelPctFor(c.vehicle.vehicleClass, c.tripKm))
        val fuel = (c.vehicle.fuel / (need * 2)).coerceIn(0.0, 1.0)
        val fatigue = (c.driver.fatigue / 100).coerceIn(0.0, 1.0)
        val cong = ((c.pickupTraffic + c.vehicleTraffic) / 2).coerceIn(0.0, 1.0)
        return Score(c.vehicle.id, 0.30 * prox + 0.20 * eta + 0.20 * suit + 0.10 * skill + 0.10 * fuel - 0.05 * fatigue - 0.05 * cong)
    }
    fun choose(all: List<Candidate>, cfg: SimConfig): Pair<Candidate, Score>? {
        var best: Pair<Candidate, Score>? = null
        for (c in all) {
            val s = score(c, cfg) ?: continue
            if (s.total < cfg.minScore) continue
            val b = best
            if (b == null) { best = c to s; continue }
            val better = abs(s.total - b.second.total) > 1e-9 && s.total > b.second.total ||
                abs(s.total - b.second.total) <= 1e-9 &&
                (c.driver.id < b.first.driver.id || (c.driver.id == b.first.driver.id && c.vehicle.id < b.first.vehicle.id))
            if (better) best = c to s
        }
        return best
    }
}

object Demand {
    fun generate(world: World, tick: Long, cfg: SimConfig): List<Contract> {
        val unlocked = world.zones.filter { it.unlocked }
        var budget = (cfg.maxOpenContracts - world.contracts.count { it.status == ContractStatus.OPEN || it.status == ContractStatus.ASSIGNED || it.status == ContractStatus.IN_PROGRESS }).coerceAtLeast(0)
        if (budget == 0) return emptyList()
        val out = mutableListOf<Contract>()
        for (zone in unlocked) {
            if (budget <= 0) break
            val rng = Rng(Rng.seed(world.seed, tick, zone.zoneId))
            val n = rng.poisson(cfg.demandPerTickPerZone * zone.demandMultiplier).coerceAtMost(budget)
            repeat(n) { i ->
                val type = when {
                    rng.nextDouble() < 0.35 -> ContractType.RIDESHARE
                    rng.nextDouble() < 0.30 -> ContractType.FOOD
                    rng.nextDouble() < 0.25 -> ContractType.PARCEL
                    else -> ContractType.CARGO
                }
                val pickupType = when (type) {
                    ContractType.FOOD -> PoiType.RESTAURANT
                    ContractType.CARGO -> PoiType.WAREHOUSE
                    ContractType.RIDESHARE -> PoiType.TRANSIT_HUB
                    ContractType.PARCEL -> PoiType.COMMERCIAL
                }
                val dropType = if (type == ContractType.CARGO) PoiType.WAREHOUSE else PoiType.RESIDENTIAL
                val pickups = City.poisIn(zone.zoneId, pickupType).ifEmpty { City.poisIn(zone.zoneId) }
                val destZone = unlocked[rng.nextInt(unlocked.size)]
                val drops = City.poisIn(destZone.zoneId, dropType).ifEmpty { City.poisIn(destZone.zoneId) }
                if (pickups.isEmpty() || drops.isEmpty()) return@repeat
                val pickup = pickups[rng.nextInt(pickups.size)]
                val drop = drops[rng.nextInt(drops.size)]
                val payload = when (type) {
                    ContractType.RIDESHARE -> Payload(75.0, description = "1-2 passengers")
                    ContractType.FOOD -> Payload(rng.range(3.0, 25.0), rng.nextDouble() < 0.45, "Food parcel")
                    ContractType.PARCEL -> Payload(rng.range(2.0, 40.0), description = "Parcel")
                    ContractType.CARGO -> Payload(rng.range(250.0, 2400.0), false, "Freight pallet")
                }
                val km = Geo.km(pickup.point(), drop.point()) * 1.35
                val deadline = tick + max(45, (km / 40.0 * 60 * 2.2).toLong())
                out += Contract(
                    id = "ctr_${tick}_${zone.zoneId}_$i", type = type, pickup = pickup, dropoff = drop,
                    payload = payload, reward = Economy.reward(type, km, payload.weightKg, zone.demandMultiplier),
                    deadlineTick = deadline, createdAtTick = tick, zoneId = zone.zoneId,
                )
                budget--
            }
        }
        return out
    }
}
