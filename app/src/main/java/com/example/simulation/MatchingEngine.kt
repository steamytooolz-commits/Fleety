package com.example.simulation

import com.example.model.Contract
import com.example.model.ContractType
import com.example.model.MatchingScore
import com.example.model.Staff
import com.example.model.StaffStatus
import com.example.model.Vehicle
import com.example.model.VehicleClass
import com.example.model.VehicleStatus
import kotlin.math.min

object MatchingEngine {
    // Deterministic weights
    const val W1_PROXIMITY = 0.25
    const val W2_ETA = 0.20
    const val W3_SUITABILITY = 0.25
    const val W4_DRIVER_SKILL = 0.15
    const val W5_FUEL_MARGIN = 0.15
    const val W6_FATIGUE = 0.15
    const val W7_CONGESTION = 0.10

    const val MAX_SEARCH_RADIUS_KM = 35.0
    const val MIN_DISPATCH_THRESHOLD = 0.35

    /**
     * Deterministic, pure scoring function for a single vehicle & contract pair.
     * No Math.random() is used anywhere.
     */
    fun scoreVehicleForContract(
        vehicle: Vehicle,
        driver: Staff?,
        contract: Contract,
        zoneTrafficIndex: Double = 0.25
    ): MatchingScore {
        // Suitability Gate
        val suitability = evaluateSuitability(vehicle.vehicleClass, contract)
        if (suitability <= 0.0) {
            return MatchingScore(
                vehicleId = vehicle.id,
                totalScore = 0.0,
                proximityScore = 0.0,
                etaScore = 0.0,
                suitabilityScore = 0.0,
                driverSkillScore = 0.0,
                fuelMarginScore = 0.0,
                fatiguePenalty = 0.0,
                congestionPenalty = 0.0
            )
        }

        // 1. Proximity score: 1 - (dist / max_radius)
        val distanceToPickup = vehicle.currentLocation.distanceTo(contract.pickup.toLatLng())
        val proximityScore = (1.0 - (distanceToPickup / MAX_SEARCH_RADIUS_KM)).coerceIn(0.0, 1.0)

        // 2. ETA score (assuming avg city speed 45 km/h)
        val estimatedEtaMinutes = (distanceToPickup / 45.0) * 60.0
        val etaScore = (1.0 - (estimatedEtaMinutes / 45.0)).coerceIn(0.0, 1.0)

        // 3. Driver skill score (1 to 10 scale -> 0.1 to 1.0)
        val driverSkill = driver?.skill ?: 3
        val driverSkillScore = (driverSkill / 10.0).coerceIn(0.1, 1.0)

        // 4. Fuel margin score
        val tripDist = contract.pickup.toLatLng().distanceTo(contract.dropoff.toLatLng()) + distanceToPickup
        val estimatedFuelBurn = tripDist * (vehicle.vehicleClass.baseBurnRate / 8.0)
        val fuelMarginScore = if (estimatedFuelBurn <= 0.01) 1.0 else {
            min(1.0, (vehicle.fuel / (estimatedFuelBurn * 2.0))).coerceIn(0.0, 1.0)
        }

        // 5. Penalties
        val fatigue = driver?.fatigue ?: 0.0
        val fatiguePenalty = (fatigue / 100.0) * 0.25
        val congestionPenalty = zoneTrafficIndex * 0.20

        val totalScore = (
            (W1_PROXIMITY * proximityScore) +
            (W2_ETA * etaScore) +
            (W3_SUITABILITY * suitability) +
            (W4_DRIVER_SKILL * driverSkillScore) +
            (W5_FUEL_MARGIN * fuelMarginScore) -
            (W6_FATIGUE * fatiguePenalty) -
            (W7_CONGESTION * congestionPenalty)
        ).coerceIn(0.0, 1.0)

        return MatchingScore(
            vehicleId = vehicle.id,
            totalScore = totalScore,
            proximityScore = proximityScore,
            etaScore = etaScore,
            suitabilityScore = suitability,
            driverSkillScore = driverSkillScore,
            fuelMarginScore = fuelMarginScore,
            fatiguePenalty = fatiguePenalty,
            congestionPenalty = congestionPenalty
        )
    }

    private fun evaluateSuitability(vehicleClass: VehicleClass, contract: Contract): Double {
        // Temperature-sensitive food requires refrigerated vehicle
        if (contract.payload.requiresRefrigeration) {
            return if (vehicleClass == VehicleClass.REFRIGERATED) 1.0 else 0.0
        }

        // Heavy freight cargo > 500kg requires truck or large van
        if (contract.type == ContractType.CARGO) {
            return when {
                vehicleClass == VehicleClass.SEDAN -> 0.0 // Sedans rejected for cargo
                contract.payload.weightKg > 1500.0 && vehicleClass != VehicleClass.TRUCK -> 0.0
                vehicleClass == VehicleClass.TRUCK -> 1.0
                vehicleClass == VehicleClass.VAN -> 0.85
                else -> 0.70
            }
        }

        // Food parcel
        if (contract.type == ContractType.FOOD) {
            return when (vehicleClass) {
                VehicleClass.REFRIGERATED -> 1.0
                VehicleClass.VAN -> 0.95
                VehicleClass.SEDAN -> 0.85
                VehicleClass.TRUCK -> 0.50 // Truck too clumsy for small food runs
            }
        }

        // Rideshare
        if (contract.type == ContractType.RIDESHARE) {
            return when (vehicleClass) {
                VehicleClass.SEDAN -> 1.0
                VehicleClass.VAN -> 0.80
                VehicleClass.REFRIGERATED -> 0.30
                VehicleClass.TRUCK -> 0.0 // Passengers in a cargo truck rejected
            }
        }

        // Parcel
        return when (vehicleClass) {
            VehicleClass.VAN -> 1.0
            VehicleClass.SEDAN -> 0.85
            VehicleClass.REFRIGERATED -> 0.75
            VehicleClass.TRUCK -> 0.60
        }
    }

    /**
     * Deterministic best match selector.
     * Tie-breaker: lowest staff.id.
     */
    fun findBestVehicle(
        vehicles: List<Vehicle>,
        drivers: Map<String, Staff>,
        contract: Contract,
        zoneTrafficIndex: Double = 0.25
    ): Pair<Vehicle, MatchingScore>? {
        val eligibleVehicles = vehicles.filter { v ->
            v.status == VehicleStatus.IDLE &&
            v.condition > 25.0 &&
            v.fuel > 15.0 &&
            v.assignedDriverId != null &&
            drivers[v.assignedDriverId]?.status == StaffStatus.ON_SHIFT &&
            (drivers[v.assignedDriverId]?.fatigue ?: 0.0) < 80.0
        }

        val scored = eligibleVehicles.map { v ->
            val driver = drivers[v.assignedDriverId]
            v to scoreVehicleForContract(v, driver, contract, zoneTrafficIndex)
        }.filter { it.second.totalScore >= MIN_DISPATCH_THRESHOLD }

        // Deterministic sort: highest totalScore first; tie-breaker: lowest staff id
        return scored.sortedWith(
            compareByDescending<Pair<Vehicle, MatchingScore>> { it.second.totalScore }
                .thenBy { it.first.assignedDriverId ?: "" }
        ).firstOrNull()
    }
}
