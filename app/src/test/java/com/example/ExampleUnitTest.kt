package com.example

import com.example.data.CityRoadNetwork
import com.example.model.Contract
import com.example.model.ContractType
import com.example.model.LatLng
import com.example.model.LocationPoint
import com.example.model.Payload
import com.example.model.Staff
import com.example.model.StaffRole
import com.example.model.StaffStatus
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import com.example.simulation.MatchingEngine
import com.example.simulation.MonteCarloInput
import com.example.simulation.MonteCarloSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDeterministicMatching_sameInputsProduceIdenticalScores() {
        val vehicle = Vehicle(
            id = "veh_test_01",
            vehicleClass = VehicleClass.VAN,
            plate = "CA-111-222",
            fuel = 85.0,
            condition = 90.0,
            currentLocation = LatLng(-33.9248, 18.4241)
        )
        val driver = Staff(
            id = "driver_01",
            name = "Test Driver",
            role = StaffRole.DRIVER,
            skill = 8,
            fatigue = 20.0,
            status = StaffStatus.ON_SHIFT
        )
        val contract = Contract(
            id = "cnt_test_01",
            type = ContractType.PARCEL,
            pickup = LocationPoint(-33.9220, 18.4230, "Shop", "cbd", "RESTAURANT"),
            dropoff = LocationPoint(-33.9045, 18.4205, "Wharf", "waterfront", "RESTAURANT"),
            payload = Payload(weightKg = 30.0, description = "Box"),
            reward = 120.0,
            deadlineTick = 50L
        )

        // Run scoring twice
        val score1 = MatchingEngine.scoreVehicleForContract(vehicle, driver, contract)
        val score2 = MatchingEngine.scoreVehicleForContract(vehicle, driver, contract)

        assertEquals("Scores must be identical (deterministic matching)", score1.totalScore, score2.totalScore, 0.000001)
        assertTrue("Eligible van should score well for standard parcel", score1.totalScore >= 0.35)
    }

    @Test
    fun testRouteInterpolation_smoothProgression() {
        val route = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 10.0),
            LatLng(10.0, 10.0)
        )

        val start = CityRoadNetwork.interpolatePosition(route, 0f)
        assertEquals(0.0, start.latitude, 0.001)
        assertEquals(0.0, start.longitude, 0.001)

        val mid = CityRoadNetwork.interpolatePosition(route, 0.5f)
        assertEquals(0.0, mid.latitude, 0.001)
        assertEquals(10.0, mid.longitude, 0.001)

        val end = CityRoadNetwork.interpolatePosition(route, 1f)
        assertEquals(10.0, end.latitude, 0.001)
        assertEquals(10.0, end.longitude, 0.001)
    }

    @Test
    fun testMonteCarloSimulator_percentileOrdering() {
        val network = CityRoadNetwork()
        val zone = network.zones.first()

        val input = MonteCarloInput(
            currentCash = 25000.0,
            targetZone = zone,
            additionalVehicles = mapOf(VehicleClass.VAN to 2),
            additionalDrivers = 2,
            marketingSpend = 1500.0
        )

        val output = MonteCarloSimulator.runSimulation(input)
        assertNotNull(output)
        assertEquals(31, output.dailyProjections.size) // day 0 to 30

        val finalDay = output.dailyProjections.last()
        assertTrue("P10 <= P50", finalDay.p10Cash <= finalDay.p50Cash)
        assertTrue("P50 <= P90", finalDay.p50Cash <= finalDay.p90Cash)
        assertTrue("Insolvency prob between 0 and 1", output.probabilityOfInsolvency in 0.0..1.0)
    }
}
