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
    fun `assign driver binds staff and vehicle and rejects a busy asset`() {
        val routes = TestRoutes { _, _ -> route }
        val world = testWorld(
            listOf(
                Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = City.base, driverId = "d1"),
                Vehicle("v2", VehicleClass.VAN, "T2", fuel = 90.0, location = City.base),
                Vehicle("v3", VehicleClass.VAN, "T3", fuel = 90.0, location = City.base,
                    status = VehicleStatus.EN_ROUTE_TO_PICKUP),
            ),
            listOf(
                Staff("d1", "D", Role.DRIVER, 0.0, 8, StaffStatus.ON_SHIFT, "v1"),
                Staff("d2", "E", Role.DRIVER, 0.0, 7, StaffStatus.ON_SHIFT),
            ),
        )
        fun assign(staffId: String, vehicleId: String) = Intent("k-$staffId-$vehicleId", "ASSIGN_DRIVER",
            kotlinx.serialization.json.buildJsonObject {
                put("staff_id", kotlinx.serialization.json.JsonPrimitive(staffId))
                put("vehicle_id", kotlinx.serialization.json.JsonPrimitive(vehicleId))
            })

        val engine = Engine(cfg)
        val first = engine.apply(world, assign("d1", "v2"), routes)
        assertTrue(first.accepted)
        assertEquals("v2", first.world.staff.first { it.id == "d1" }.vehicleId)
        assertEquals("d1", first.world.vehicles.first { it.id == "v2" }.driverId)
        assertNull(first.world.vehicles.first { it.id == "v1" }.driverId)

        val busy = engine.apply(first.world, assign("d2", "v3"), routes)
        assertFalse(busy.accepted, "a moving vehicle cannot take a new driver")
    }

    @Test
    fun `recruitment charges the placement fee`() {
        val routes = TestRoutes { _, _ -> route }
        val world = testWorld(
            listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = City.base)),
            emptyList(),
        ).copy(cash = 1000.0)
        val intent = Intent("hire-1", "HIRE_STAFF", kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive("New Driver"))
            put("role", kotlinx.serialization.json.JsonPrimitive("driver"))
            put("skill", kotlinx.serialization.json.JsonPrimitive(7))
        })
        val outcome = Engine(cfg).apply(world, intent, routes)
        assertTrue(outcome.accepted)
        assertEquals(650.0, outcome.world.cash, 0.001)
        assertTrue(outcome.world.ledger.any { it.category == TxCategory.SALARY && it.amount == -350.0 })
    }

    @Test
    fun `lifetime counters accumulate outside the windowed contract list`() {
        val routes = TestRoutes { _, _ -> route }
        val world = testWorld(
            listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = City.base,
                status = VehicleStatus.UNLOADING, loadingTicks = 1, contractId = "c1", driverId = "d1")),
            listOf(Staff("d1", "D", Role.DRIVER, 0.0, 8, StaffStatus.ON_SHIFT, "v1")),
        ).copy(
            contracts = listOf(Contract("c1", ContractType.PARCEL, City.pois[2], City.pois[3], Payload(5.0), 120.0, 500,
                ContractStatus.IN_PROGRESS, "v1", "d1", completedAtTick = null)),
            lifetimeCompleted = 4,
            lifetimeRevenue = 800.0,
        )
        val next = Engine(cfg).step(world, routes).world
        assertEquals(5, next.lifetimeCompleted)
        assertEquals(920.0, next.lifetimeRevenue, 0.01)
        assertEquals(ContractStatus.COMPLETED, next.contracts.first { it.id == "c1" }.status)
    }

    @Test
    fun `freight tiers price express and bulk above standard`() {
        val standard = Economy.reward(ContractType.PARCEL, 10.0, 20.0, 1.0, ContractTier.STANDARD)
        val express = Economy.reward(ContractType.PARCEL, 10.0, 20.0, 1.0, ContractTier.EXPRESS)
        val bulk = Economy.reward(ContractType.PARCEL, 10.0, 20.0, 1.0, ContractTier.BULK)
        assertTrue(express > standard * 1.5, "express should carry a premium")
        assertTrue(bulk > standard)
        // The board actually offers tiers, not just a pricing curve.
        val world = City.initialWorld("p1").copy(campaigns = listOf(Campaign("c1", "cbd", 10, 6000.0, 3.0, 0.0)))
        val tiers = (1L..60L).flatMap { tick ->
            Demand.generate(world.copy(tick = tick - 1, contracts = emptyList()), tick, cfg).map { it.tier }
        }.toSet()
        assertTrue(ContractTier.EXPRESS in tiers, "express work should appear on the board")
        assertTrue(ContractTier.STANDARD in tiers)
    }

    @Test
    fun `depot purchase is discounted and drawn down before pump fuel`() {
        val routes = TestRoutes { _, _ -> route }
        val world = testWorld(listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 20.0, location = City.base)), emptyList())
            .copy(cash = 5000.0, autoDispatch = false, fuelPrice = 1.20, fuelReserve = 0.0)
        val engine = Engine(cfg)
        val buy = engine.apply(world, Intent("buy-1", "BUY_FUEL", kotlinx.serialization.json.buildJsonObject {
            put("litres", kotlinx.serialization.json.JsonPrimitive(200.0))
        }), routes)
        assertTrue(buy.accepted)
        assertEquals(200.0, buy.world.fuelReserve, 0.001)
        assertEquals(5000.0 - 200.0 * 1.20 * 0.92, buy.world.cash, 0.01)

        val refuel = engine.apply(buy.world, Intent("refuel-1", "REFUEL", kotlinx.serialization.json.buildJsonObject {
            put("vehicle_id", kotlinx.serialization.json.JsonPrimitive("v1"))
        }), routes)
        assertTrue(refuel.accepted)
        assertEquals(100.0, refuel.world.vehicles.first().fuel, 0.001)
        // The van tank holds 70 L and was 20% full: 56 L comes from the reserve, nothing from the pump.
        assertEquals(144.0, refuel.world.fuelReserve, 0.001)
        assertEquals(buy.world.cash, refuel.world.cash, 0.001)
    }

    @Test
    fun `campaign raises demand in its zone`() {
        val base = City.initialWorld("p1")
        fun count(world: World): Int = (1L..120L).sumOf { tick ->
            Demand.generate(world.copy(tick = tick - 1, contracts = emptyList()), tick, cfg).count { it.zoneId == "cbd" }
        }
        val plain = count(base)
        val boosted = count(base.copy(campaigns = listOf(Campaign("c1", "cbd", 120, 6000.0, 1.0, 0.0))))
        assertTrue(boosted > plain, "campaign should raise demand ($plain -> $boosted)")
    }

    @Test
    fun `loan draws against the credit line and charges interest on payroll`() {
        val routes = TestRoutes { _, _ -> route }
        val engine = Engine(cfg)
        var world = testWorld(listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = City.base)), emptyList())
            .copy(cash = 1000.0, creditLimit = 400.0, tick = 0, lastPayrollTick = 0, autoDispatch = false)
        fun take(key: String) = Intent(key, "TAKE_LOAN", kotlinx.serialization.json.buildJsonObject {
            put("amount", kotlinx.serialization.json.JsonPrimitive(5000.0))
        })
        assertFalse(engine.apply(world, take("loan-1"), routes).accepted, "credit line is enforced")

        world = world.copy(creditLimit = 5000.0)
        val drawn = engine.apply(world, take("loan-2"), routes)
        assertTrue(drawn.accepted)
        assertEquals(6000.0, drawn.world.cash, 0.001)
        assertNotNull(drawn.world.loan)
        assertFalse(engine.apply(drawn.world, take("loan-3"), routes).accepted, "one operating loan at a time")

        val paid = engine.step(drawn.world.copy(tick = 29, lastPayrollTick = 0, cash = 10000.0), routes).world
        assertTrue(paid.ledger.any { it.category == TxCategory.LOAN && it.description.contains("interest") })
        assertTrue((paid.loan?.remaining ?: 0.0) < 5000.0, "scheduled repayment should reduce the principal")
    }

    @Test
    fun `workshop service restores condition after the bay countdown`() {
        val routes = TestRoutes { _, _ -> route }
        var world = testWorld(
            listOf(Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, condition = 40.0, location = City.base)),
            listOf(Staff("m1", "M", Role.MECHANIC, 0.0, 9, StaffStatus.ON_SHIFT)),
        ).copy(cash = 2000.0, autoDispatch = false)
        val engine = Engine(cfg)
        val booked = engine.apply(world, Intent("svc-1", "SCHEDULE_SERVICE", kotlinx.serialization.json.buildJsonObject {
            put("vehicle_id", kotlinx.serialization.json.JsonPrimitive("v1"))
        }), routes)
        assertTrue(booked.accepted)
        assertEquals(VehicleStatus.MAINTENANCE, booked.world.vehicles.first().status)
        assertTrue(booked.world.ledger.any { it.category == TxCategory.REPAIR })
        assertTrue(booked.world.vehicles.first().serviceTicks in 2..cfg.serviceTicks)

        var after = booked.world
        repeat(6) { after = engine.step(after, routes).world }
        assertEquals(VehicleStatus.IDLE, after.vehicles.first().status)
        assertEquals(100.0, after.vehicles.first().condition, 0.001)
    }

    @Test
    fun `city events spawn on schedule and slow every leg`() {
        val routes = TestRoutes { _, _ -> route }
        val staff = Staff("d1", "D", Role.DRIVER, 0.0, 8, StaffStatus.ON_SHIFT, "v1")
        val moving = Vehicle("v1", VehicleClass.VAN, "T1", fuel = 90.0, location = route.first(), route = route,
            routeKm = Geo.total(route), status = VehicleStatus.EN_ROUTE_TO_DROPOFF, contractId = "c1", driverId = "d1")
        val contract = Contract("c1", ContractType.PARCEL, City.pois[2], City.pois[3], Payload(5.0), 90.0, 500,
            ContractStatus.IN_PROGRESS, "v1", "d1")
        fun world(tick: Long, event: CityEvent?) = testWorld(listOf(moving), listOf(staff))
            .copy(tick = tick, contracts = listOf(contract), cityEvent = event, autoDispatch = false)
        val engine = Engine(cfg)
        val clearKm = engine.step(world(200, null), routes).world.vehicles.first().travelledKm
        val rainKm = engine.step(world(200, CityEvent(EventKind.RAIN, "Rain", 10, speedFactor = 0.5)), routes)
            .world.vehicles.first().travelledKm
        assertTrue(rainKm < clearKm * 0.75, "rain should slow the leg ($clearKm -> $rainKm)")

        val scheduled = engine.step(world(119, null), routes)
        assertNotNull(scheduled.world.cityEvent, "an event should start at the scheduled tick")
        assertTrue(scheduled.events.any { it.type == "city" })
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
