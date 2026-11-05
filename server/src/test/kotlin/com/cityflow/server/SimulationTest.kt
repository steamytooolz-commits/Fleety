package com.cityflow.server

import kotlin.test.*

class SimulationTest {
    private val cfg = SimConfig()

    private fun testWorld(vehicles: List<Vehicle>, staff: List<Staff>) =
        City.initialWorld("p1").copy(vehicles = vehicles, staff = staff, contracts = emptyList())

    private val route = listOf(LngLat(18.420, -33.925), LngLat(18.424, -33.925), LngLat(18.424, -33.920), LngLat(18.430, -33.920))

    @Test
    fun `position stays on the route polyline`() {
        val total = Geo.total(route)
        for (f in 0..20) {
            val pos = Geo.at(route, total * f / 20.0)
            assertTrue(Geo.distanceToRoute(pos.point, route) < 0.02, "progress $f left the polyline")
        }
        assertEquals(route.first().lng, Geo.at(route, 0.0).point.lng, 1e-9)
        assertTrue(Geo.at(route, total).arrived)
    }

    @Test
    fun `vehicle follows the routed geometry instead of cutting corners`() {
        val routes = TestRoutes { _, _ -> route }
        val world = testWorld(
            listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = route.first(), route = route,
                routeKm = Geo.total(route), status = VehicleStatus.EN_ROUTE_TO_PICKUP,
                contractId = "c1", driverId = "d1")),
            listOf(Staff("d1", "D", Role.DRIVER, 10.0, 8, StaffStatus.ON_SHIFT, "v1")),
        ).copy(contracts = listOf(Contract("c1", ContractType.PARCEL, City.pois[2], City.pois[3], Payload(5.0), 90.0, 500,
            ContractStatus.ASSIGNED, "v1", "d1")))
        val after = Engine(cfg).step(world, routes).world.vehicles.first()
        assertTrue(after.travelledKm > 0.0)
        assertTrue(Geo.distanceToRoute(after.location, route) < 0.05, "vehicle left the road geometry")
    }

    @Test
    fun `matching is deterministic and independent of candidate order`() {
        val contract = Contract("c1", ContractType.PARCEL, City.pois[2], City.pois[3], Payload(10.0), 80.0, 200)
        val v1 = Vehicle("v1", VehicleClass.VAN, "A", fuel = 90.0, location = City.base, driverId = "d1")
        val v2 = Vehicle("v2", VehicleClass.VAN, "B", fuel = 90.0, location = City.base, driverId = "d2")
        val d1 = Staff("d1", "A", Role.DRIVER, 10.0, 7, StaffStatus.ON_SHIFT, "v1")
        val d2 = Staff("d2", "B", Role.DRIVER, 10.0, 7, StaffStatus.ON_SHIFT, "v2")
        fun candidates(a: Vehicle, b: Vehicle, da: Staff, db: Staff) = listOf(
            Matching.Candidate(contract, a, da, 1.0, 5.0, 0.3, 0.3),
            Matching.Candidate(contract, b, db, 1.0, 5.0, 0.3, 0.3),
        )
        val first = Matching.choose(candidates(v1, v2, d1, d2), cfg)!!.first.vehicle.id
        val second = Matching.choose(candidates(v2, v1, d2, d1), cfg)!!.first.vehicle.id
        assertEquals(first, second)
        assertEquals("v1", first, "tie breaks on lowest driver id")
        assertEquals(first, Matching.choose(candidates(v1, v2, d1, d2), cfg)!!.first.vehicle.id)
    }

    @Test
    fun `suitability rejects wrong vehicles`() {
        assertNull(Matching.suitability(ContractType.CARGO, Payload(900.0), VehicleClass.SEDAN))
        assertNull(Matching.suitability(ContractType.CARGO, Payload(900.0), VehicleClass.VAN))
        assertNull(Matching.suitability(ContractType.FOOD, Payload(5.0, true), VehicleClass.VAN))
        assertNotNull(Matching.suitability(ContractType.CARGO, Payload(900.0), VehicleClass.TRUCK))
        assertNotNull(Matching.suitability(ContractType.FOOD, Payload(5.0, true), VehicleClass.REFRIGERATED))
    }

    @Test
    fun `ignored low fuel breaks down after three ticks and charges a tow`() {
        val routes = TestRoutes { _, _ -> route }
        var world = testWorld(
            listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 10.0, location = route.first(), route = route,
                routeKm = Geo.total(route), status = VehicleStatus.EN_ROUTE_TO_PICKUP,
                contractId = "c1", driverId = "d1")),
            listOf(Staff("d1", "D", Role.DRIVER, 10.0, 8, StaffStatus.ON_SHIFT, "v1")),
        ).copy(contracts = listOf(Contract("c1", ContractType.PARCEL, City.pois[2], City.pois[3], Payload(5.0), 90.0, 500,
            ContractStatus.ASSIGNED, "v1", "d1")))
        val engine = Engine(cfg)
        repeat(4) { world = engine.step(world, routes).world }
        assertTrue(world.incidents.any { it.type == IncidentType.LOW_FUEL })
        assertTrue(world.incidents.any { it.type == IncidentType.BREAKDOWN && it.severity == 3 })
        assertEquals(VehicleStatus.BREAKDOWN, world.vehicles.first().status)
        assertTrue(world.ledger.any { it.category == TxCategory.TOW })
    }

    @Test
    fun `payroll never drives cash negative and triggers a strike`() {
        val routes = TestRoutes { _, _ -> route }
        val world = testWorld(
            listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = City.base)),
            listOf(Staff("d1", "D", Role.DRIVER, 0.0, 8, StaffStatus.ON_SHIFT, "v1", salaryPerDay = 500.0)),
        ).copy(cash = 10.0, tick = 29, lastPayrollTick = 0)
        val next = Engine(cfg).step(world, routes).world
        assertTrue(next.cash >= 0.0)
        assertTrue(next.incidents.any { it.type == IncidentType.STRIKE && it.severity == 3 })
    }

    @Test
    fun `assignment intent is rejected the second time`() {
        val routes = TestRoutes { _, _ -> route }
        val world = testWorld(
            listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = City.base, driverId = "d1")),
            listOf(Staff("d1", "D", Role.DRIVER, 0.0, 8, StaffStatus.ON_SHIFT, "v1")),
        ).copy(contracts = listOf(Contract("c1", ContractType.PARCEL, City.pois[2], City.pois[3], Payload(5.0), 90.0, 500)))
        val engine = Engine(cfg)
        val intent = Intent("key-1", "ASSIGN_CONTRACT", kotlinx.serialization.json.buildJsonObject {
            put("contract_id", kotlinx.serialization.json.JsonPrimitive("c1")); put("vehicle_id", kotlinx.serialization.json.JsonPrimitive("v1"))
        })
        val first = engine.apply(world, intent, routes)
        assertTrue(first.accepted)
        val second = engine.apply(first.world, intent, routes)
        assertFalse(second.accepted)
    }

    @Test
    fun `monte carlo is reproducible and ordered`() {
        val world = City.initialWorld("p1").copy(cash = 20000.0)
        val req = ExpansionRequest(additionalVehicles = 2, additionalDrivers = 2, marketingSpend = 2000.0, days = 30, iterations = 200)
        val a = MonteCarlo.run(world, req, cfg)
        val b = MonteCarlo.run(world, req, cfg)
        assertEquals(a.p50, b.p50)
        assertTrue(a.p10 <= a.p50 && a.p50 <= a.p90)
        assertTrue(a.insolvencyProbability in 0.0..1.0)
    }
}
