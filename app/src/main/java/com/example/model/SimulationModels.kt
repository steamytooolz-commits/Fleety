package com.example.model

import kotlin.math.sqrt

data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    fun distanceTo(other: LatLng): Double {
        // Approximate planar distance in km
        val latDiff = (other.latitude - latitude) * 111.0
        val lngDiff = (other.longitude - longitude) * 111.0 * kotlin.math.cos(Math.toRadians(latitude))
        return sqrt(latDiff * latDiff + lngDiff * lngDiff)
    }
}

data class Player(
    val id: String = "player_1",
    val email: String = "operator@cityflow.local",
    val companyName: String = "Apex City Logistics",
    val baseLocation: LatLng = LatLng(-33.9249, 18.4241), // Cape Town CBD Base
    val cash: Double = 0.0,
    val level: Int = 1,
    val tick: Long = 0L,
    val autoDispatchEnabled: Boolean = true,
    val paused: Boolean = false,
    val speed: Double = 1.0
)

enum class StaffRole {
    DRIVER,
    DISPATCHER,
    MECHANIC,
    MANAGER
}

enum class StaffStatus {
    IDLE,
    ON_SHIFT,
    RESTING,
    FIRED
}

data class Staff(
    val id: String,
    val owner: String = "player_1",
    val name: String,
    val role: StaffRole,
    val fatigue: Double = 0.0,      // 0 - 100
    val skill: Int = 5,             // 1 - 10
    val status: StaffStatus = StaffStatus.IDLE,
    val assignedVehicleId: String? = null,
    val salaryPerDay: Double = 120.0
)

enum class VehicleClass(
    val label: String,
    val tankSize: Double,        // liters
    val baseBurnRate: Double,    // fuel units per tick
    val payloadCapKg: Double,
    val dailyUpkeep: Double,
    val purchasePrice: Double,
    val requiresRefrigeration: Boolean = false
) {
    SEDAN("Sedan Cruiser", 50.0, 0.45, 180.0, 45.0, 8500.0),
    VAN("Express Van", 70.0, 0.60, 850.0, 65.0, 14000.0),
    TRUCK("Heavy Cargo Truck", 120.0, 0.95, 3500.0, 110.0, 26000.0),
    REFRIGERATED("ChillVan Fresh", 80.0, 0.75, 1200.0, 90.0, 21000.0, requiresRefrigeration = true)
}

enum class VehicleStatus {
    IDLE,
    EN_ROUTE_TO_PICKUP,
    AT_PICKUP,
    LOADING,
    EN_ROUTE_TO_DROPOFF,
    UNLOADING,
    DIVERT_TO_REFUEL,
    BREAKDOWN,
    FORCED_REST,
    MAINTENANCE
}

data class Vehicle(
    val id: String,
    val owner: String = "player_1",
    val vehicleClass: VehicleClass,
    val plate: String,
    val fuel: Double = 100.0,           // 0 - 100%
    val condition: Double = 100.0,      // 0 - 100%
    val mileage: Double = 0.0,          // total km
    val status: VehicleStatus = VehicleStatus.IDLE,
    val currentLocation: LatLng,
    val currentRoute: List<LatLng> = emptyList(),
    val routeProgress: Float = 0f,      // 0.0 to 1.0
    val assignedDriverId: String? = null,
    val currentContractId: String? = null,
    val heading: Float = 0f,            // bearing in degrees for marker rendering
    val lowFuelTicks: Int = 0           // consecutive ticks with fuel < 15
)

enum class ContractType(val label: String, val baseColorHex: Long) {
    RIDESHARE("Passenger Rideshare", 0xFF00E5FF),
    FOOD("Fresh & Food Parcel", 0xFFFFB300),
    PARCEL("Express Package", 0xFF00E676),
    CARGO("Heavy Freight", 0xFFB388FF)
}

enum class ContractStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val zoneId: String,
    val poiType: String
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}

data class Payload(
    val weightKg: Double,
    val requiresRefrigeration: Boolean = false,
    val description: String = "Goods"
)

data class Contract(
    val id: String,
    val type: ContractType,
    val pickup: LocationPoint,
    val dropoff: LocationPoint,
    val payload: Payload,
    val reward: Double,
    val deadlineTick: Long,
    val status: ContractStatus = ContractStatus.OPEN,
    val assignedVehicleId: String? = null,
    val assignedDriverId: String? = null,
    val createdAtTick: Long = 0L,
    val completedAtTick: Long? = null
)

enum class IncidentType(val label: String, val defaultCost: Double) {
    LOW_FUEL("Critical Fuel Alert", 120.0),
    BREAKDOWN("Mechanical Engine Failure", 350.0),
    ACCIDENT("Minor Collision Delay", 450.0),
    STRIKE("Labor Union Protest", 800.0),
    TRAFFIC_JAM("Severe Gridlock", 0.0),
    FATIGUE("Driver Fatigue Alert", 0.0)
}

data class Incident(
    val id: String,
    val vehicleId: String?,
    val type: IncidentType,
    val severity: Int,              // 1 to 3 (3 triggers simulation clock pause!)
    val resolved: Boolean = false,
    val costIfIgnored: Double,
    val description: String,
    val createdTick: Long
)

enum class TransactionCategory {
    REVENUE,
    FUEL,
    SALARY,
    REPAIR,
    TOW,
    EXPANSION,
    PURCHASE
}

data class Transaction(
    val id: String,
    val owner: String = "player_1",
    val tick: Long,
    val category: TransactionCategory,
    val amount: Double,             // Positive for revenue, negative for expenses
    val contractId: String? = null,
    val description: String
)

data class ZoneBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    fun contains(latLng: LatLng): Boolean {
        return latLng.latitude in minLat..maxLat && latLng.longitude in minLng..maxLng
    }
}

data class CityZone(
    val zoneId: String,
    val name: String,
    val demandMultiplier: Double,
    val trafficIndex: Double,       // 0.0 (smooth) to 1.0 (heavy gridlock)
    val unlocked: Boolean,
    val unlockCost: Double,
    val completionRateThreshold: Double,
    val completedContracts: Int = 0,
    val bounds: ZoneBounds,
    val center: LatLng
)

sealed class RealtimeEvent<T> {
    data class Created<T>(val item: T) : RealtimeEvent<T>()
    data class Updated<T>(val item: T) : RealtimeEvent<T>()
    data class Deleted<T>(val id: String) : RealtimeEvent<T>()
}

data class MatchingScore(
    val vehicleId: String,
    val totalScore: Double,
    val proximityScore: Double,
    val etaScore: Double,
    val suitabilityScore: Double,
    val driverSkillScore: Double,
    val fuelMarginScore: Double,
    val fatiguePenalty: Double,
    val congestionPenalty: Double
)
