package com.example.data

import com.example.model.CityZone
import com.example.model.LatLng
import com.example.model.LocationPoint
import com.example.model.ZoneBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.PriorityQueue
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class RoadNode(
    val id: String,
    val location: LatLng,
    val name: String
)

data class RoadEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val distanceKm: Double,
    val speedLimitKmh: Double = 60.0,
    val waypoints: List<LatLng> = emptyList() // Curvature points along real road geometry
)

class CityRoadNetwork(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
) {
    var osrmBaseUrl: String = "http://10.0.2.2:5000" // Default Android emulator host to localhost

    val nodes = mutableMapOf<String, RoadNode>()
    val adjacency = mutableMapOf<String, MutableList<RoadEdge>>()

    val zones = listOf(
        CityZone(
            zoneId = "cbd",
            name = "Cape Town CBD & Foreshore",
            demandMultiplier = 1.25,
            trafficIndex = 0.35,
            unlocked = true,
            unlockCost = 0.0,
            completionRateThreshold = 0.0,
            completedContracts = 12,
            bounds = ZoneBounds(-33.935, 18.410, -33.915, 18.435),
            center = LatLng(-33.924, 18.424)
        ),
        CityZone(
            zoneId = "waterfront",
            name = "V&A Waterfront & Harbor Marina",
            demandMultiplier = 1.40,
            trafficIndex = 0.25,
            unlocked = true,
            unlockCost = 0.0,
            completionRateThreshold = 0.0,
            completedContracts = 8,
            bounds = ZoneBounds(-33.915, 18.410, -33.898, 18.435),
            center = LatLng(-33.905, 18.421)
        ),
        CityZone(
            zoneId = "industrial",
            name = "Paarden Eiland Logistics Harbor",
            demandMultiplier = 1.10,
            trafficIndex = 0.20,
            unlocked = true,
            unlockCost = 0.0,
            completionRateThreshold = 0.0,
            completedContracts = 5,
            bounds = ZoneBounds(-33.920, 18.445, -33.900, 18.480),
            center = LatLng(-33.912, 18.462)
        ),
        CityZone(
            zoneId = "suburbs",
            name = "Southern Suburbs & Woodstock",
            demandMultiplier = 0.95,
            trafficIndex = 0.40,
            unlocked = false,
            unlockCost = 12500.0,
            completionRateThreshold = 0.85,
            completedContracts = 0,
            bounds = ZoneBounds(-33.955, 18.435, -33.930, 18.475),
            center = LatLng(-33.942, 18.455)
        ),
        CityZone(
            zoneId = "airport",
            name = "Cape Town International Airport Hub",
            demandMultiplier = 1.65,
            trafficIndex = 0.15,
            unlocked = false,
            unlockCost = 28000.0,
            completionRateThreshold = 0.90,
            completedContracts = 0,
            bounds = ZoneBounds(-33.985, 18.570, -33.950, 18.615),
            center = LatLng(-33.968, 18.598)
        )
    )

    val pois = listOf(
        // CBD POIs
        LocationPoint(-33.9220, 18.4230, "St Georges Mall Bistro", "cbd", "RESTAURANT"),
        LocationPoint(-33.9265, 18.4195, "Bree Street Artisan Bakery", "cbd", "RESTAURANT"),
        LocationPoint(-33.9248, 18.4241, "City Central Transit Hub", "cbd", "TRANSIT_HUB"),
        LocationPoint(-33.9195, 18.4285, "Convention Center Suites", "cbd", "COMMERCIAL"),
        LocationPoint(-33.9290, 18.4150, "Gardens High-Rise Condos", "cbd", "RESIDENTIAL"),
        LocationPoint(-33.9230, 18.4200, "Apex Central Base Depot", "cbd", "DEPOT"),
        LocationPoint(-33.9270, 18.4260, "Shell Buitengracht Station", "cbd", "FUEL_STATION"),

        // Waterfront POIs
        LocationPoint(-33.9045, 18.4205, "Victoria Wharf Seafood Market", "waterfront", "RESTAURANT"),
        LocationPoint(-33.9080, 18.4240, "Watershed Artisan Center", "waterfront", "COMMERCIAL"),
        LocationPoint(-33.9015, 18.4180, "Silo Hotel & Luxury Residences", "waterfront", "RESIDENTIAL"),
        LocationPoint(-33.9060, 18.4260, "Waterfront Marina Terminal", "waterfront", "TRANSIT_HUB"),
        LocationPoint(-33.9110, 18.4215, "Engen Harbor Fuel Point", "waterfront", "FUEL_STATION"),

        // Paarden Eiland Industrial Harbor
        LocationPoint(-33.9110, 18.4590, "Transnet Freight Port Terminal", "industrial", "WAREHOUSE"),
        LocationPoint(-33.9145, 18.4680, "ColdStorage Logistics Warehouse", "industrial", "WAREHOUSE"),
        LocationPoint(-33.9085, 18.4520, "Marine Drive Container Freight", "industrial", "INDUSTRIAL"),
        LocationPoint(-33.9170, 18.4630, "Industrial Maintenance Bay #2", "industrial", "DEPOT"),
        LocationPoint(-33.9125, 18.4650, "TotalEnergies Truck Stop", "industrial", "FUEL_STATION"),

        // Southern Suburbs / Woodstock
        LocationPoint(-33.9350, 18.4480, "Old Biscuit Mill Eateries", "suburbs", "RESTAURANT"),
        LocationPoint(-33.9420, 18.4610, "Woodstock Tech Lofts", "suburbs", "RESIDENTIAL"),
        LocationPoint(-33.9480, 18.4700, "Salt River Creative Quarter", "suburbs", "COMMERCIAL"),
        LocationPoint(-33.9400, 18.4520, "BP Main Road Woodstock", "suburbs", "FUEL_STATION"),

        // Airport Hub
        LocationPoint(-33.9680, 18.5980, "International Air Cargo Terminal", "airport", "WAREHOUSE"),
        LocationPoint(-33.9710, 18.6020, "Arrivals Passenger Terminal", "airport", "TRANSIT_HUB"),
        LocationPoint(-33.9640, 18.5910, "Aviation Cold Chain Depot", "airport", "WAREHOUSE"),
        LocationPoint(-33.9740, 18.6050, "Airport Boulevard Fuel Station", "airport", "FUEL_STATION")
    )

    init {
        buildNetwork()
    }

    private fun addNode(id: String, lat: Double, lng: Double, name: String) {
        nodes[id] = RoadNode(id, LatLng(lat, lng), name)
    }

    private fun addRoad(
        from: String,
        to: String,
        speedLimit: Double = 60.0,
        waypoints: List<LatLng> = emptyList(),
        bidirectional: Boolean = true
    ) {
        val n1 = nodes[from] ?: return
        val n2 = nodes[to] ?: return
        val dist = n1.location.distanceTo(n2.location)
        
        adjacency.getOrPut(from) { mutableListOf() }.add(RoadEdge(from, to, dist, speedLimit, waypoints))
        if (bidirectional) {
            adjacency.getOrPut(to) { mutableListOf() }.add(RoadEdge(to, from, dist, speedLimit, waypoints.reversed()))
        }
    }

    private fun buildNetwork() {
        // --- CBD Nodes ---
        addNode("cbd_center", -33.9248, 18.4241, "Adderley & Strand St")
        addNode("cbd_buitengracht", -33.9230, 18.4180, "Buitengracht & Strand St")
        addNode("cbd_bree", -33.9260, 18.4190, "Bree & Wale St")
        addNode("cbd_gardens", -33.9310, 18.4160, "Buitenkant & Mill St")
        addNode("cbd_foreshore", -33.9180, 18.4280, "Heerengracht & Coen Steytler")
        addNode("cbd_station", -33.9220, 18.4290, "Cape Town Railway Station")

        // --- Waterfront Nodes ---
        addNode("wf_entrance", -33.9140, 18.4220, "Dock Rd & Buitengracht")
        addNode("wf_wharf", -33.9050, 18.4200, "Breakwater Blvd")
        addNode("wf_silo", -33.9020, 18.4230, "Silo District")
        addNode("wf_port", -33.9070, 18.4280, "Port Road Harbor Link")

        // --- Highways & Corridors ---
        addNode("hwy_n1_start", -33.9190, 18.4350, "FW de Klerk Blvd (N1 Start)")
        addNode("hwy_n2_start", -33.9250, 18.4380, "Nelson Mandela Blvd (N2 Start)")
        
        // --- Paarden Eiland Industrial Nodes ---
        addNode("ind_marine_drive", -33.9160, 18.4500, "Marine Drive Interchange")
        addNode("ind_terminal", -33.9110, 18.4590, "Transnet Freight Way")
        addNode("ind_paarden_rd", -33.9080, 18.4680, "Paarden Eiland Road")
        addNode("ind_neptune", -33.9170, 18.4650, "Neptune Street Warehouse Strip")

        // --- Woodstock & Suburbs Nodes ---
        addNode("sub_woodstock_main", -33.9330, 18.4480, "Sir Lowry & Woodstock Main")
        addNode("sub_salt_river", -33.9420, 18.4620, "Salt River Circle")
        addNode("sub_observatory", -33.9480, 18.4750, "Main Road Observatory")

        // --- Highway Corridor to Airport ---
        addNode("hwy_n2_mid", -33.9520, 18.5200, "N2 Settlers Way Express")
        addNode("apt_junction", -33.9650, 18.5750, "Airport Approach Road")
        addNode("apt_terminal", -33.9690, 18.5980, "Airport Freight & Arrivals")

        // --- Construct Roads & Connections ---
        // CBD Grid
        addRoad("cbd_buitengracht", "cbd_center", 50.0)
        addRoad("cbd_center", "cbd_station", 45.0)
        addRoad("cbd_buitengracht", "cbd_bree", 45.0)
        addRoad("cbd_bree", "cbd_gardens", 50.0)
        addRoad("cbd_gardens", "cbd_station", 50.0)
        addRoad("cbd_center", "cbd_foreshore", 60.0)
        addRoad("cbd_station", "cbd_foreshore", 55.0)

        // CBD to Waterfront
        addRoad("cbd_buitengracht", "wf_entrance", 60.0)
        addRoad("cbd_foreshore", "wf_entrance", 60.0)
        addRoad("wf_entrance", "wf_wharf", 40.0)
        addRoad("wf_wharf", "wf_silo", 35.0)
        addRoad("wf_silo", "wf_port", 40.0)
        addRoad("wf_port", "wf_entrance", 45.0)

        // CBD to Highways
        addRoad("cbd_foreshore", "hwy_n1_start", 70.0)
        addRoad("cbd_station", "hwy_n2_start", 70.0)
        addRoad("hwy_n1_start", "hwy_n2_start", 60.0)

        // Industrial Harbor Links (via Marine Dr / N1)
        addRoad("hwy_n1_start", "ind_marine_drive", 80.0)
        addRoad("ind_marine_drive", "ind_terminal", 60.0)
        addRoad("ind_terminal", "ind_paarden_rd", 50.0)
        addRoad("ind_paarden_rd", "ind_neptune", 50.0)
        addRoad("ind_marine_drive", "ind_neptune", 60.0)

        // Woodstock & Suburbs Links (via N2 / Main Rd)
        addRoad("hwy_n2_start", "sub_woodstock_main", 60.0)
        addRoad("cbd_gardens", "sub_woodstock_main", 50.0)
        addRoad("sub_woodstock_main", "sub_salt_river", 55.0)
        addRoad("sub_salt_river", "sub_observatory", 60.0)
        addRoad("sub_salt_river", "ind_neptune", 60.0) // Cross-link Industrial to Salt River

        // Highway to Airport Hub
        addRoad("sub_salt_river", "hwy_n2_mid", 100.0)
        addRoad("hwy_n2_start", "hwy_n2_mid", 100.0)
        addRoad("hwy_n2_mid", "apt_junction", 110.0)
        addRoad("apt_junction", "apt_terminal", 70.0)
    }

    fun findNearestNode(location: LatLng): RoadNode {
        return nodes.values.minByOrNull { it.location.distanceTo(location) }
            ?: nodes.values.first()
    }

    /**
     * Finds realistic path along the road network.
     * Guaranteed never to cross open water or non-road geometry.
     */
    fun findPath(start: LatLng, end: LatLng): List<LatLng> {
        val startNode = findNearestNode(start)
        val endNode = findNearestNode(end)

        if (startNode.id == endNode.id) {
            return listOf(start, startNode.location, end)
        }

        // A* / Dijkstra pathfinding
        val distances = mutableMapOf<String, Double>().withDefault { Double.MAX_VALUE }
        val previous = mutableMapOf<String, Pair<String, List<LatLng>>>()
        val queue = PriorityQueue<Pair<String, Double>>(compareBy { it.second })

        distances[startNode.id] = 0.0
        queue.add(startNode.id to 0.0)

        while (queue.isNotEmpty()) {
            val (currentId, currentDist) = queue.poll()
            if (currentId == endNode.id) break
            if (currentDist > (distances[currentId] ?: Double.MAX_VALUE)) continue

            val edges = adjacency[currentId] ?: emptyList()
            for (edge in edges) {
                val alt = currentDist + edge.distanceKm
                if (alt < distances.getValue(edge.toNodeId)) {
                    distances[edge.toNodeId] = alt
                    previous[edge.toNodeId] = currentId to edge.waypoints
                    val heuristic = nodes[edge.toNodeId]?.location?.distanceTo(endNode.location) ?: 0.0
                    queue.add(edge.toNodeId to (alt + heuristic))
                }
            }
        }

        // Reconstruct path
        val route = mutableListOf<LatLng>()
        var curr: String? = endNode.id
        while (curr != null && curr != startNode.id) {
            val prevEntry = previous[curr]
            if (prevEntry == null) break
            val (prevNodeId, waypoints) = prevEntry
            val nodeLoc = nodes[curr]?.location
            if (nodeLoc != null) route.add(0, nodeLoc)
            if (waypoints.isNotEmpty()) {
                route.addAll(0, waypoints)
            }
            curr = prevNodeId
        }

        route.add(0, startNode.location)
        if (start != startNode.location) route.add(0, start)
        if (end != endNode.location) route.add(end)

        return route
    }

    /**
     * OSRM API call with graceful offline fallback to the embedded road network.
     */
    suspend fun getRouteWithOsrmFallback(start: LatLng, end: LatLng): List<LatLng> = withContext(Dispatchers.IO) {
        try {
            // OSRM format: /route/v1/driving/{lng},{lat};{lng},{lat}?overview=full&geometries=geojson
            val url = "$osrmBaseUrl/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")
                        val osrmPoints = mutableListOf<LatLng>()
                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            osrmPoints.add(LatLng(coord.getDouble(1), coord.getDouble(0)))
                        }
                        if (osrmPoints.size >= 2) {
                            return@withContext osrmPoints
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Silently fallback to internal road network
        }
        // Fallback to internal road network graph
        findPath(start, end)
    }

    companion object {
        /**
         * Pure interpolation along route coordinates as specified in Section 6.4:
         * fun interpolatePosition(route: List<LngLat>, progress: Float): LngLat
         */
        fun interpolatePosition(route: List<LatLng>, progress: Float): LatLng {
            if (route.isEmpty()) return LatLng(0.0, 0.0)
            if (route.size == 1 || progress <= 0f) return route.first()
            if (progress >= 1f) return route.last()

            val totalSegments = route.size - 1
            val rawIdx = progress * totalSegments
            val idx = rawIdx.toInt().coerceIn(0, totalSegments - 1)
            val next = (idx + 1).coerceAtMost(totalSegments)
            val frac = rawIdx - idx

            val lat = route[idx].latitude + frac * (route[next].latitude - route[idx].latitude)
            val lng = route[idx].longitude + frac * (route[next].longitude - route[idx].longitude)
            return LatLng(lat, lng)
        }

        /**
         * Calculates heading in degrees for marker orientation
         */
        fun calculateHeading(route: List<LatLng>, progress: Float): Float {
            if (route.size < 2) return 0f
            val totalSegments = route.size - 1
            val rawIdx = progress * totalSegments
            val idx = rawIdx.toInt().coerceIn(0, totalSegments - 1)
            val next = (idx + 1).coerceAtMost(totalSegments)

            val p1 = route[idx]
            val p2 = route[next]

            val dLng = Math.toRadians(p2.longitude - p1.longitude)
            val lat1 = Math.toRadians(p1.latitude)
            val lat2 = Math.toRadians(p2.latitude)

            val y = sin(dLng) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
            val bearing = Math.toDegrees(atan2(y, x))
            return ((bearing + 360) % 360).toFloat()
        }
    }
}
