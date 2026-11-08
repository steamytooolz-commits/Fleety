package com.example.data

import com.example.model.CityZone
import com.example.model.Contract
import com.example.model.ContractStatus
import com.example.model.ContractType
import com.example.model.Incident
import com.example.model.IncidentType
import com.example.model.LatLng
import com.example.model.LocationPoint
import com.example.model.Payload
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class ConnectionState { CONNECTING, ONLINE, OFFLINE }

/**
 * Server-authoritative PocketBase client.
 *
 * The Kotlin tick service owns all simulation state. This repository only:
 *  - authenticates the player (demo account is auto-provisioned on first launch),
 *  - loads a snapshot of the collections the client renders,
 *  - applies PocketBase realtime SSE deltas (with a polling safety net),
 *  - posts operator intents with idempotency keys for the server to validate.
 *
 * It never mutates game state locally.
 */
class PocketBaseRepository(
    private val roadNetwork: CityRoadNetwork,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val restClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Volatile var pocketBaseUrl: String = "http://10.0.2.2:8090"
    val isRemoteConnected: Boolean get() = _connection.value == ConnectionState.ONLINE

    private val _connection = MutableStateFlow(ConnectionState.CONNECTING)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

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

    private val vehicleEvents = MutableSharedFlow<RealtimeEvent<Vehicle>>(64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val contractEvents = MutableSharedFlow<RealtimeEvent<Contract>>(64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val incidentEvents = MutableSharedFlow<RealtimeEvent<Incident>>(32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile private var token: String? = null
    @Volatile private var userId: String? = null
    @Volatile private var connectionJob: Job? = null
    private val intentCounter = AtomicInteger(0)
    private val seenTransactions = mutableSetOf<String>()
    private val processedIdempotencyKeys = mutableSetOf<String>()

    fun start() = configure(pocketBaseUrl)

    /** Point the client at a PocketBase origin and (re)connect. */
    fun configure(url: String) {
        pocketBaseUrl = url.trim().trimEnd('/').ifBlank { "http://10.0.2.2:8090" }
        token = null
        userId = null
        connectionJob?.cancel()
        _connection.value = ConnectionState.CONNECTING
        connectionJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                try {
                    ensureAuth()
                    refreshAll()
                    _connection.value = ConnectionState.ONLINE
                    launch { pollLoop() }
                    streamRealtime()
                } catch (e: Exception) {
                    _connection.value = ConnectionState.OFFLINE
                    token = null
                    userId = null
                }
                delay(2500)
            }
        }
    }

    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
    }

    // --- Realtime streams ---

    fun observeVehicles(): Flow<RealtimeEvent<Vehicle>> = callbackFlow {
        val job = scope.launch { vehicleEvents.collect { trySend(it) } }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    fun observeContracts(): Flow<RealtimeEvent<Contract>> = callbackFlow {
        val job = scope.launch { contractEvents.collect { trySend(it) } }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    fun observeIncidents(): Flow<RealtimeEvent<Incident>> = callbackFlow {
        val job = scope.launch { incidentEvents.collect { trySend(it) } }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    // --- Intents (the only way state changes) ---

    fun assignContract(contractId: String, vehicleId: String) = sendIntent(
        "ASSIGN_CONTRACT", JSONObject().put("contract_id", contractId).put("vehicle_id", vehicleId)
    )

    fun purchaseVehicle(vehicleClass: VehicleClass) = sendIntent(
        "PURCHASE_VEHICLE", JSONObject().put("class", vehicleClass.name.lowercase())
    )

    fun refuelVehicle(vehicleId: String) = sendIntent("REFUEL", JSONObject().put("vehicle_id", vehicleId))

    fun serviceVehicle(vehicleId: String) = sendIntent("SERVICE", JSONObject().put("vehicle_id", vehicleId))

    fun hireStaff(name: String, role: StaffRole, skill: Int) = sendIntent(
        "HIRE_STAFF", JSONObject().put("name", name).put("role", role.name.lowercase()).put("skill", skill)
    )

    fun toggleStaffShift(staffId: String) = sendIntent("TOGGLE_SHIFT", JSONObject().put("staff_id", staffId))

    fun assignDriver(staffId: String, vehicleId: String?) = sendIntent(
        "ASSIGN_DRIVER",
        JSONObject().put("staff_id", staffId).put("vehicle_id", vehicleId ?: ""),
    )

    fun unlockZone(zoneId: String) = sendIntent("UNLOCK_ZONE", JSONObject().put("zone_id", zoneId))

    fun setAutoDispatch(enabled: Boolean) = sendIntent("SET_AUTO_DISPATCH", JSONObject().put("enabled", enabled))

    fun spawnContract() = sendIntent("SPAWN_CONTRACT")

    fun setPaused(paused: Boolean) = sendIntent("SET_PAUSED", JSONObject().put("paused", paused))

    fun forceStep() = sendIntent("FORCE_STEP")

    fun resolveIncident(incidentId: String, uiAction: String) {
        val action = when (uiAction.uppercase()) {
            "DISPATCH_FUEL_TRUCK" -> "fuel_truck"
            "REROUTE_STATION" -> "reroute_to_station"
            "TOW_TO_BASE" -> "tow"
            "FIELD_REPAIR" -> "field_repair"
            "TAKE_LOAN" -> "loan"
            "PAY_BONUS" -> "pay_partial"
            else -> uiAction.lowercase()
        }
        sendIntent("RESOLVE_INCIDENT", JSONObject().put("incident_id", incidentId).put("action", action))
    }

    fun sendIntent(type: String, payload: JSONObject = JSONObject()) {
        val uid = userId ?: return
        scope.launch {
            try {
                val key = "android_${type.lowercase()}_${System.currentTimeMillis()}_${intentCounter.incrementAndGet()}"
                request("POST", "/api/collections/intents/records", JSONObject().apply {
                    put("owner", uid)
                    put("idempotency_key", key)
                    put("type", type)
                    put("payload", payload)
                    put("status", "pending")
                }, true)
            } catch (_: Exception) {
                // Offline: the UI list will refresh from the next successful snapshot.
            }
        }
    }

    fun checkAndRecordIdempotency(key: String): Boolean = synchronized(processedIdempotencyKeys) {
        processedIdempotencyKeys.add(key)
    }

    // --- Auth & snapshot ---

    private suspend fun ensureAuth() {
        if (token != null && userId != null) return
        val credentials = JSONObject().put("identity", DEMO_EMAIL).put("password", DEMO_PASSWORD)
        val auth = try {
            request("POST", "/api/collections/players/auth-with-password", credentials, false)
        } catch (_: Exception) {
            provisionDemoPlayer()
            request("POST", "/api/collections/players/auth-with-password", credentials, false)
        }
        token = auth.getString("token")
        userId = auth.getJSONObject("record").getString("id")
    }

    private suspend fun provisionDemoPlayer() {
        request("POST", "/api/collections/players/records", JSONObject().apply {
            put("email", DEMO_EMAIL)
            put("password", DEMO_PASSWORD)
            put("passwordConfirm", DEMO_PASSWORD)
            put("company_name", "Apex City Logistics")
            put("cash", 18500.0)
            put("level", 1)
            put("tick", 0)
            put("seed", 1337)
            put("auto_dispatch", true)
        }, false)
    }

    private suspend fun refreshAll() {
        val uid = userId ?: return
        val me = request("GET", "/api/collections/players/records/$uid", null, true)
        _player.value = mapPlayer(me)

        fetchAll("vehicles", uid).forEach { record -> mapVehicle(record)?.let { upsertVehicle(it) } }
        fetchAll("staff", uid).forEach { record -> mapStaff(record)?.let { upsertStaff(it) } }
        fetchAll("contracts", uid).forEach { record -> mapContract(record)?.let { upsertContract(it) } }
        fetchAll("incidents", uid).forEach { record -> mapIncident(record)?.let { upsertIncident(it) } }
        fetchAll("city_state", uid).let { records -> mergeZones(records) }
        fetchAll("transactions", uid).forEach { record -> mapTransaction(record)?.let { addTransaction(it) } }
    }

    private suspend fun fetchAll(collection: String, ownerId: String): List<JSONObject> {
        val filter = java.net.URLEncoder.encode("owner=\"$ownerId\"", "UTF-8")
        val response = request("GET", "/api/collections/$collection/records?perPage=500&filter=$filter", null, true)
        val items = response.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { items.getJSONObject(it) }
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            delay(2500)
            try {
                refreshAll()
                _connection.value = ConnectionState.ONLINE
            } catch (_: Exception) {
                _connection.value = ConnectionState.OFFLINE
            }
        }
    }

    // --- PocketBase realtime SSE ---

    private suspend fun streamRealtime() = withContext(Dispatchers.IO) {
        val authHeader = token ?: throw IOException("not authenticated")
        val request = Request.Builder()
            .url("$pocketBaseUrl/api/realtime")
            .header("Authorization", authHeader)
            .header("Accept", "text/event-stream")
            .build()
        sseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("realtime HTTP ${response.code}")
            val source = response.body?.source() ?: throw IOException("empty realtime body")
            var event = "message"
            val data = StringBuilder()
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) handleEvent(event, data.toString())
                        event = "message"
                        data.clear()
                    }
                }
            }
        }
    }

    private suspend fun handleEvent(event: String, data: String) {
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        if (event == "PB_CONNECT") {
            val clientId = json.optString("clientId")
            if (clientId.isNotBlank()) subscribe(clientId)
            return
        }
        val action = json.optString("action")
        val record = json.optJSONObject("record") ?: return
        if (action == "delete") {
            val ref = record.optString("ref_id").ifBlank { record.optString("id") }
            when (event) {
                "vehicles" -> removeVehicle(ref)
                "staff" -> removeStaff(ref)
                "contracts" -> removeContract(ref)
                "incidents" -> removeIncident(ref)
            }
            return
        }
        when (event) {
            "players" -> _player.value = mapPlayer(record)
            "vehicles" -> mapVehicle(record)?.let { upsertVehicle(it) }
            "staff" -> mapStaff(record)?.let { upsertStaff(it) }
            "contracts" -> mapContract(record)?.let { upsertContract(it) }
            "incidents" -> mapIncident(record)?.let { upsertIncident(it) }
            "transactions" -> mapTransaction(record)?.let { addTransaction(it) }
            "city_state" -> mergeZones(listOf(record))
        }
    }

    private suspend fun subscribe(clientId: String) {
        try {
            request("POST", "/api/realtime", JSONObject().apply {
                put("clientId", clientId)
                put("subscriptions", JSONArray(listOf("players", "vehicles", "staff", "contracts", "incidents", "transactions", "city_state")))
            }, true)
        } catch (_: Exception) {
            // Polling keeps the snapshot fresh if realtime cannot subscribe.
        }
    }

    // --- HTTP plumbing ---

    private suspend fun request(method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url("$pocketBaseUrl$path")
            if (auth) token?.let { builder.header("Authorization", it) }
            val requestBody = body?.toString()?.toRequestBody(jsonType)
            builder.method(method, requestBody ?: if (method == "GET") null else "{}".toRequestBody(jsonType))
            restClient.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = if (text.isBlank()) JSONObject() else JSONObject(text)
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${json.optString("message").ifBlank { text.take(200) }}")
                }
                json
            }
        }

    // --- Record mappers ---

    private fun mapPlayer(o: JSONObject): Player {
        val base = o.optJSONObject("base_location")
        return Player(
            id = o.optString("id", "player_1"),
            email = o.optString("email", DEMO_EMAIL),
            companyName = o.optString("company_name", "Apex City Logistics").ifBlank { "Apex City Logistics" },
            baseLocation = base?.let { LatLng(it.optDouble("lat", -33.9249), it.optDouble("lng", 18.4241)) }
                ?: LatLng(-33.9249, 18.4241),
            cash = o.optDouble("cash", 0.0),
            level = o.optInt("level", 1),
            xp = o.optLong("xp", 0L),
            reputation = o.optDouble("reputation", 100.0),
            totalRevenue = o.optDouble("total_revenue", 0.0),
            contractsCompleted = o.optInt("contracts_completed", 0),
            contractsFailed = o.optInt("contracts_failed", 0),
            distanceKm = o.optDouble("distance_km", 0.0),
            tick = o.optLong("tick", 0L),
            autoDispatchEnabled = o.optBoolean("auto_dispatch", true),
            paused = o.optBoolean("paused", false),
        )
    }

    private fun mapVehicle(o: JSONObject): Vehicle? {
        val ref = o.optString("ref_id").ifBlank { return null }
        val loc = o.optJSONObject("current_location")
        val routeArray = o.optJSONArray("current_route") ?: JSONArray()
        val route = (0 until routeArray.length()).mapNotNull { i ->
            val p = routeArray.optJSONArray(i) ?: return@mapNotNull null
            if (p.length() < 2) null else LatLng(p.optDouble(0), p.optDouble(1))
        }
        return Vehicle(
            id = ref,
            vehicleClass = runCatching { VehicleClass.valueOf(o.optString("class").uppercase()) }.getOrDefault(VehicleClass.VAN),
            plate = o.optString("plate", ref),
            fuel = o.optDouble("fuel", 100.0),
            condition = o.optDouble("condition", 100.0),
            mileage = o.optDouble("mileage", 0.0),
            status = runCatching { VehicleStatus.valueOf(o.optString("status").uppercase()) }.getOrDefault(VehicleStatus.IDLE),
            currentLocation = loc?.let { LatLng(it.optDouble("lat", -33.9249), it.optDouble("lng", 18.4241)) }
                ?: LatLng(-33.9249, 18.4241),
            currentRoute = route,
            routeProgress = o.optDouble("route_progress", 0.0).toFloat().coerceIn(0f, 1f),
            routeKm = o.optDouble("route_km", 0.0),
            travelledKm = o.optDouble("distance_travelled_km", 0.0),
            assignedDriverId = o.optString("assigned_driver").ifBlank { null },
            currentContractId = o.optString("contract").ifBlank { null },
            heading = o.optDouble("heading", 0.0).toFloat(),
            lowFuelTicks = o.optInt("low_fuel_ticks", 0),
        )
    }

    private fun mapStaff(o: JSONObject): Staff? {
        val ref = o.optString("ref_id").ifBlank { return null }
        return Staff(
            id = ref,
            owner = o.optString("owner"),
            name = o.optString("name", ref),
            role = runCatching { StaffRole.valueOf(o.optString("role").uppercase()) }.getOrDefault(StaffRole.DRIVER),
            fatigue = o.optDouble("fatigue", 0.0),
            skill = o.optInt("skill", 5),
            status = runCatching { StaffStatus.valueOf(o.optString("status").uppercase()) }.getOrDefault(StaffStatus.IDLE),
            assignedVehicleId = o.optString("assigned_vehicle").ifBlank { null },
            salaryPerDay = o.optDouble("salary_per_day", 120.0),
            morale = o.optDouble("morale", 100.0),
            restTicks = o.optInt("rest_ticks", 0),
        )
    }

    private fun mapContract(o: JSONObject): Contract? {
        val ref = o.optString("ref_id").ifBlank { return null }
        val pickup = o.optJSONObject("pickup") ?: return null
        val dropoff = o.optJSONObject("dropoff") ?: return null
        fun place(p: JSONObject) = LocationPoint(
            latitude = p.optDouble("lat", -33.9249),
            longitude = p.optDouble("lng", 18.4241),
            label = p.optString("label", "Waypoint"),
            zoneId = p.optString("zone_id", "cbd"),
            poiType = p.optString("poi_type", "COMMERCIAL"),
        )
        val payload = o.optJSONObject("payload")
        return Contract(
            id = ref,
            type = runCatching { ContractType.valueOf(o.optString("type").uppercase()) }.getOrDefault(ContractType.PARCEL),
            pickup = place(pickup),
            dropoff = place(dropoff),
            payload = Payload(
                weightKg = payload?.optDouble("weight_kg", 0.0) ?: 0.0,
                requiresRefrigeration = payload?.optBoolean("requires_refrigeration", false) ?: false,
            ),
            reward = o.optDouble("reward", 0.0),
            deadlineTick = o.optLong("deadline_tick", 0L),
            status = runCatching { ContractStatus.valueOf(o.optString("status").uppercase()) }.getOrDefault(ContractStatus.OPEN),
            assignedVehicleId = o.optString("assigned_vehicle").ifBlank { null },
            assignedDriverId = o.optString("assigned_driver").ifBlank { null },
            createdAtTick = o.optLong("created_tick", 0L),
            completedAtTick = o.optLong("completed_tick", 0L).takeIf { it > 0L },
            routeKm = o.optDouble("route_km", 0.0),
            zoneId = pickup.optString("zone_id").ifBlank { "cbd" },
        )
    }

    private fun mapIncident(o: JSONObject): Incident? {
        val ref = o.optString("ref_id").ifBlank { return null }
        val type = runCatching { IncidentType.valueOf(o.optString("type").uppercase()) }.getOrNull()
            ?: return null
        return Incident(
            id = ref,
            vehicleId = o.optString("vehicle").ifBlank { null },
            type = type,
            severity = o.optInt("severity", 1),
            resolved = o.optBoolean("resolved", false),
            costIfIgnored = o.optDouble("cost_if_ignored", 0.0),
            description = o.optString("description", ""),
            createdTick = o.optLong("created_tick", 0L),
            driverId = o.optString("driver").ifBlank { null },
            zoneId = o.optString("zone_id").ifBlank { null },
        )
    }

    private fun mapTransaction(o: JSONObject): Transaction? {
        val ref = o.optString("ref_id").ifBlank { return null }
        val category = when (o.optString("category").lowercase()) {
            "revenue" -> TransactionCategory.REVENUE
            "fuel" -> TransactionCategory.FUEL
            "salary" -> TransactionCategory.SALARY
            "repair" -> TransactionCategory.REPAIR
            "tow" -> TransactionCategory.TOW
            "expansion" -> TransactionCategory.EXPANSION
            "purchase" -> TransactionCategory.PURCHASE
            "penalty" -> TransactionCategory.PENALTY
            "loan" -> TransactionCategory.LOAN
            else -> TransactionCategory.REVENUE
        }
        return Transaction(
            id = ref,
            owner = o.optString("owner"),
            tick = o.optLong("tick", 0L),
            category = category,
            amount = o.optDouble("amount", 0.0),
            contractId = o.optString("contract").ifBlank { null },
            description = o.optString("description", ""),
        )
    }

    private fun mergeZones(records: List<JSONObject>) {
        if (records.isEmpty()) return
        val stored = _zones.value.associateBy { it.zoneId }
        val byId = records.associateBy { it.optString("zone_id") }
        _zones.value = roadNetwork.zones.map { base ->
            val record = byId[base.zoneId]
                ?: return@map (stored[base.zoneId] ?: base)
            base.copy(
                demandMultiplier = record.optDouble("demand_multiplier", base.demandMultiplier),
                trafficIndex = record.optDouble("traffic_index", base.trafficIndex),
                unlocked = record.optBoolean("unlocked", base.unlocked),
                unlockCost = record.optDouble("unlock_cost", base.unlockCost),
                completionRateThreshold = record.optDouble("completion_threshold", base.completionRateThreshold),
                completedContracts = record.optInt("completed_contracts", base.completedContracts),
                failedContracts = record.optInt("failed_contracts", base.failedContracts),
                unmetDemand = record.optInt("unmet_demand", base.unmetDemand),
            )
        }
    }

    // --- Local snapshot upserts (driven only by server events) ---

    private fun upsertVehicle(v: Vehicle) {
        _vehicles.value = _vehicles.value.toMutableMap().apply { put(v.id, v) }
        vehicleEvents.tryEmit(RealtimeEvent.Updated(v))
    }

    private fun removeVehicle(id: String) {
        _vehicles.value = _vehicles.value.toMutableMap().apply { remove(id) }
    }

    private fun upsertStaff(s: Staff) {
        _staff.value = _staff.value.toMutableMap().apply { put(s.id, s) }
    }

    private fun removeStaff(id: String) {
        _staff.value = _staff.value.toMutableMap().apply { remove(id) }
    }

    private fun upsertContract(c: Contract) {
        val existing = _contracts.value[c.id]
        _contracts.value = _contracts.value.toMutableMap().apply { put(c.id, c) }
        contractEvents.tryEmit(if (existing == null) RealtimeEvent.Created(c) else RealtimeEvent.Updated(c))
    }

    private fun removeContract(id: String) {
        _contracts.value = _contracts.value.toMutableMap().apply { remove(id) }
    }

    private fun upsertIncident(i: Incident) {
        val existing = _incidents.value[i.id]
        _incidents.value = _incidents.value.toMutableMap().apply { put(i.id, i) }
        if (existing == null) incidentEvents.tryEmit(RealtimeEvent.Created(i))
    }

    private fun removeIncident(id: String) {
        _incidents.value = _incidents.value.toMutableMap().apply { remove(id) }
    }

    private fun addTransaction(t: Transaction) {
        synchronized(seenTransactions) {
            if (!seenTransactions.add(t.id)) return
            _transactions.value = (_transactions.value + t).sortedByDescending { it.tick }.take(200)
        }
    }

    companion object {
        private const val DEMO_EMAIL = "demo@cityflow.local"
        private const val DEMO_PASSWORD = "cityflow123"
    }
}
