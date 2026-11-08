package com.cityflow.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.*
import kotlin.math.round

class OsrmRouteProvider(private val baseUrl: String) : RouteProvider {
    private val client = HttpClient(CIO) { expectSuccess = false }
    private val cache = mutableMapOf<String, Route>()
    private val json = Json { ignoreUnknownKeys = true }
    override fun route(from: LngLat, to: LngLat): Route? {
        val key = "${from.lng},${from.lat};${to.lng},${to.lat}"
        cache[key]?.let { return it }
        val url = "${baseUrl.trimEnd('/')}/route/v1/driving/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson"
        val body = runCatching {
            kotlinx.coroutines.runBlocking { client.get(url).bodyAsText() }
        }.getOrNull() ?: return null
        val route = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            val r = obj["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val coords = r["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map {
                val c = it.jsonArray
                LngLat(c[0].jsonPrimitive.double, c[1].jsonPrimitive.double)
            }
            Route(coords, (r["distance"]?.jsonPrimitive?.double ?: 0.0) / 1000.0,
                (r["duration"]?.jsonPrimitive?.double ?: 0.0) / 60.0)
        }.getOrNull() ?: return null
        if (cache.size > 5000) cache.clear()
        cache[key] = route
        return route
    }
    fun close() = client.close()
}

/** Deterministic multi-vertex test route provider (never used in production routing). */
class TestRoutes(private val shape: (LngLat, LngLat) -> List<LngLat>) : RouteProvider {
    override fun route(from: LngLat, to: LngLat): Route {
        val geometry = shape(from, to)
        return Route(geometry, Geo.total(geometry), Geo.total(geometry) / 40.0 * 60)
    }
}

data class StoredIntent(val recordId: String, val playerId: String, val key: String, val type: String, val payload: JsonObject)

interface Store {
    suspend fun loadAll(): List<World>
    suspend fun save(world: World)
    suspend fun pendingIntents(): List<StoredIntent>
    suspend fun finishIntent(intent: StoredIntent, accepted: Boolean, message: String)
    fun close() {}
}

class MemoryStore(var world: World) : Store {
    private val processed = mutableSetOf<String>()
    private val intents = mutableListOf<StoredIntent>()
    override suspend fun loadAll() = listOf(world)
    override suspend fun save(world: World) { this.world = world }
    override suspend fun pendingIntents() = intents.filter { it.key !in processed }
    override suspend fun finishIntent(intent: StoredIntent, accepted: Boolean, message: String) { processed += intent.key }
    fun submit(intent: StoredIntent) { if (intents.none { it.key == intent.key }) intents += intent }
}

/**
 * PocketBase REST store. The tick service authenticates as a superuser and writes the collections
 * created by backend/pocketbase/pb_migrations. Domain ids are kept in `ref_id`; the PocketBase record
 * id is cached so repeated ticks update instead of duplicate.
 */
class PocketBaseStore(
    private val baseUrl: String,
    private val email: String,
    private val password: String,
) : Store {
    private val client = HttpClient(CIO) { expectSuccess = false }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val recordIds = mutableMapOf<String, String>()
    private val writtenTransactions = mutableSetOf<String>()
    private var token: String? = null

    private suspend fun auth(): String {
        token?.let { return it }
        val res = client.post("$baseUrl/api/collections/_superusers/auth-with-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"identity":${JsonPrimitive(email)},"password":${JsonPrimitive(password)}}""")
        }
        val obj = json.parseToJsonElement(res.bodyAsText()).jsonObject
        token = obj["token"]?.jsonPrimitive?.content ?: error("PocketBase admin auth failed")
        return token!!
    }

    private suspend fun get(path: String): JsonObject {
        val res = client.get("$baseUrl$path") { header("Authorization", auth()) }
        return json.parseToJsonElement(res.bodyAsText()).jsonObject
    }

    private suspend fun write(method: String, path: String, body: String): JsonObject {
        val res = client.request("$baseUrl$path") {
            this.method = HttpMethod.parse(method)
            header("Authorization", auth())
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return runCatching { json.parseToJsonElement(res.bodyAsText()).jsonObject }.getOrElse { JsonObject(emptyMap()) }
    }

    private suspend fun upsert(collection: String, refId: String, ownerId: String, fields: JsonObject): String {
        val key = "$ownerId:$collection:$refId"
        recordIds[key]?.let { id ->
            write("PATCH", "/api/collections/$collection/records/$id", fields.toString())
            return id
        }
        val created = write("POST", "/api/collections/$collection/records", fields.toString())
        val id = created["id"]?.jsonPrimitive?.content ?: refId
        recordIds[key] = id
        return id
    }

    override suspend fun loadAll(): List<World> {
        val users = get("/api/collections/players/records?perPage=200")["items"]?.jsonArray ?: return emptyList()
        return users.mapNotNull { el ->
            val u = el.jsonObject
            val pid = u["id"]!!.jsonPrimitive.content
            val data = listOf("vehicles", "staff", "contracts", "incidents", "transactions", "city_state").associateWith { coll ->
                (get("/api/collections/$coll/records?perPage=500&filter=" + urlEncode("owner=\"$pid\""))["items"]?.jsonArray ?: JsonArray(emptyList()))
            }
            // Remember PocketBase record ids so a restarted process updates instead of re-creating.
            data.forEach { (coll, items) ->
                items.forEach { el ->
                    val o = el.jsonObject
                    val ref = o.str("ref_id").ifBlank { o.str("zone_id") }
                    val recordId = o.str("id")
                    if (ref.isNotBlank() && recordId.isNotBlank()) recordIds["$pid:$coll:$ref"] = recordId
                }
            }
            runCatching { toWorld(u, data) }
                .onFailure { System.err.println("Could not map player $pid: ${it.message}") }
                .getOrNull()
        }
    }

    private fun toWorld(player: JsonObject, data: Map<String, JsonArray>): World {
        val pid = player["id"]!!.jsonPrimitive.content
        fun arr(c: String) = data[c] ?: JsonArray(emptyList())
        val storedZones = arr("city_state").map { it.jsonObject }.associateBy { z -> z.str("zone_id") }
        val zones = City.zones().map { base ->
            val z = storedZones[base.zoneId] ?: return@map base
            base.copy(
                demandMultiplier = z.num("demand_multiplier", base.demandMultiplier),
                trafficIndex = z.num("traffic_index", base.trafficIndex),
                unlocked = z["unlocked"]?.jsonPrimitive?.booleanOrNull ?: base.unlocked,
                completed = z.num("completed_contracts", base.completed.toDouble()).toInt(),
                failed = z.num("failed_contracts", base.failed.toDouble()).toInt(),
                unmet = z.num("unmet_demand", base.unmet.toDouble()).toInt(),
            )
        }
        val staff = arr("staff").map { it.jsonObject }.map { s ->
            Staff(
                id = s.str("ref_id"), name = s.str("name"),
                role = runCatching { Role.valueOf(s.str("role").uppercase()) }.getOrDefault(Role.DRIVER),
                fatigue = s.num("fatigue", 0.0), skill = s.num("skill", 5.0).toInt(),
                status = runCatching { StaffStatus.valueOf(s.str("status").uppercase()) }.getOrDefault(StaffStatus.IDLE),
                vehicleId = s["assigned_vehicle"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                salaryPerDay = s.num("salary_per_day", 120.0), restTicks = s.num("rest_ticks", 0.0).toInt(),
                morale = s.num("morale", 100.0),
            )
        }
        val vehicles = arr("vehicles").map { it.jsonObject }.map { v ->
            // Records written by an older client can hold JSON nulls; never throw on those.
            val loc = v["current_location"] as? JsonObject
            val route = (v["current_route"] as? JsonArray)?.mapNotNull { p ->
                val a = p.jsonArray
                if (a.size >= 2) LngLat(a[1].jsonPrimitive.double, a[0].jsonPrimitive.double) else null
            } ?: emptyList()
            Vehicle(
                id = v.str("ref_id"), vehicleClass = runCatching { VehicleClass.valueOf(v.str("class").uppercase()) }.getOrDefault(VehicleClass.VAN),
                plate = v.str("plate"), fuel = v.num("fuel", 100.0), condition = v.num("condition", 100.0),
                mileageKm = v.num("mileage", 0.0), status = runCatching { VehicleStatus.valueOf(v.str("status").uppercase()) }.getOrDefault(VehicleStatus.IDLE),
                location = if (loc != null) LngLat(loc.num("lng", 18.424), loc.num("lat", -33.9249)) else City.base,
                route = route, routeKm = v.num("route_km", 0.0), travelledKm = v.num("distance_travelled_km", 0.0),
                routeProgress = v.num("route_progress", 0.0),
                driverId = v["assigned_driver"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                contractId = v["contract"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                lowFuelTicks = v.num("low_fuel_ticks", 0.0).toInt(), heading = v.num("heading", 0.0),
            )
        }
        val contracts = arr("contracts").map { it.jsonObject }.mapNotNull { c ->
            val pu = c["pickup"] as? JsonObject ?: return@mapNotNull null
            val dr = c["dropoff"] as? JsonObject ?: return@mapNotNull null
            val payload = c["payload"] as? JsonObject
            Contract(
                id = c.str("ref_id"),
                type = runCatching { ContractType.valueOf(c.str("type").uppercase()) }.getOrDefault(ContractType.PARCEL),
                pickup = Place(pu.num("lat", 0.0), pu.num("lng", 0.0), pu.str("label"), pu.str("zone_id"), PoiType.COMMERCIAL),
                dropoff = Place(dr.num("lat", 0.0), dr.num("lng", 0.0), dr.str("label"), dr.str("zone_id"), PoiType.RESIDENTIAL),
                payload = Payload(payload?.num("weight_kg", 0.0) ?: 0.0, payload?.get("requires_refrigeration")?.jsonPrimitive?.booleanOrNull ?: false),
                reward = c.num("reward", 0.0), deadlineTick = c.num("deadline_tick", 0.0).toLong(),
                status = runCatching { ContractStatus.valueOf(c.str("status").uppercase()) }.getOrDefault(ContractStatus.OPEN),
                vehicleId = c["assigned_vehicle"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                driverId = c["assigned_driver"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                createdAtTick = c.num("created_tick", 0.0).toLong(), completedAtTick = c["completed_tick"]?.jsonPrimitive?.longOrNull,
                routeKm = c.num("route_km", 0.0), zoneId = pu.str("zone_id"),
            )
        }
        return World(
            playerId = pid, companyName = player.str("company_name").ifBlank { "Operator" },
            base = (player["base_location"] as? JsonObject)?.let { LngLat(it.num("lng", 18.424), it.num("lat", -33.9249)) } ?: City.base,
            cash = player.num("cash", 0.0), level = player.num("level", 1.0).toInt(),
            tick = player.num("tick", 0.0).toLong(), seed = player.num("seed", 1337.0).toLong(),
            zones = zones, staff = staff, vehicles = vehicles, contracts = contracts,
            incidents = arr("incidents").map { it.jsonObject }.mapNotNull { i ->
                val type = runCatching { IncidentType.valueOf(i.str("type").uppercase()) }.getOrNull() ?: return@mapNotNull null
                Incident(i.str("ref_id"), i["vehicle"]?.jsonPrimitive?.contentOrNull, i["driver"]?.jsonPrimitive?.contentOrNull,
                    i["contract"]?.jsonPrimitive?.contentOrNull, i["zone_id"]?.jsonPrimitive?.contentOrNull, type,
                    i.num("severity", 1.0).toInt(), i["resolved"]?.jsonPrimitive?.booleanOrNull ?: false,
                    i.num("cost_if_ignored", 0.0), i.str("description"), i.num("created_tick", 0.0).toLong())
            },
            ledger = arr("transactions").map { it.jsonObject }.map { t ->
                Transaction(t.str("ref_id"), t.num("tick", 0.0).toLong(),
                    runCatching { TxCategory.valueOf(t.str("category").uppercase()) }.getOrDefault(TxCategory.REVENUE),
                    t.num("amount", 0.0), t["contract"]?.jsonPrimitive?.contentOrNull, t.str("description"),
                    t["idempotency_key"]?.jsonPrimitive?.contentOrNull)
            },
            lastPayrollTick = player.num("last_payroll_tick", 0.0).toLong(),
            autoDispatch = player["auto_dispatch"]?.jsonPrimitive?.booleanOrNull ?: true,
            paused = player["paused"]?.jsonPrimitive?.booleanOrNull ?: false,
            speed = player.num("speed", 1.0).coerceIn(1.0, 4.0),
            lifetimeCompleted = player.num("contracts_completed", 0.0).toInt(),
            lifetimeFailed = player.num("contracts_failed", 0.0).toInt(),
            lifetimeRevenue = player.num("total_revenue", 0.0),
        )
    }

    override suspend fun save(world: World) {
        val pid = world.playerId
        // Progression is derived from lifetime counters so it survives the windowed collections.
        val completed = world.lifetimeCompleted
        val failed = world.lifetimeFailed
        val xp = completed * XP_PER_CONTRACT
        val level = 1 + (xp / XP_PER_LEVEL).toInt()
        val attempts = completed + failed
        val reputation = if (attempts == 0) 100.0 else completed * 100.0 / attempts
        val distanceKm = world.vehicles.sumOf { it.mileageKm }
        val playerFields = buildJsonObject {
            put("company_name", world.companyName); put("cash", world.cash); put("level", level)
            put("xp", xp); put("reputation", round(reputation * 10) / 10.0)
            put("contracts_completed", completed); put("contracts_failed", failed)
            put("total_revenue", round(world.lifetimeRevenue * 100) / 100.0)
            put("distance_km", round(distanceKm * 10) / 10.0)
            put("tick", world.tick); put("seed", world.seed); put("last_payroll_tick", world.lastPayrollTick)
            put("auto_dispatch", world.autoDispatch); put("paused", world.paused); put("speed", world.speed)
            put("base_location", buildJsonObject { put("lat", world.base.lat); put("lng", world.base.lng) })
        }
        write("PATCH", "/api/collections/players/records/$pid", playerFields.toString())

        for (v in world.vehicles) upsert("vehicles", v.id, pid, buildJsonObject {
            put("owner", pid); put("ref_id", v.id); put("class", v.vehicleClass.name.lowercase())
            put("plate", v.plate); put("fuel", v.fuel); put("condition", v.condition); put("mileage", v.mileageKm)
            put("status", v.status.name.lowercase()); put("route_progress", v.routeProgress)
            put("route_km", v.routeKm); put("distance_travelled_km", v.travelledKm); put("low_fuel_ticks", v.lowFuelTicks)
            put("heading", v.heading)
            put("current_location", buildJsonObject { put("lat", v.location.lat); put("lng", v.location.lng) })
            put("current_route", buildJsonArray { v.route.forEach { p -> add(buildJsonArray { add(p.lat); add(p.lng) }) } })
            put("assigned_driver", v.driverId); put("contract", v.contractId)
        })
        for (s in world.staff) upsert("staff", s.id, pid, buildJsonObject {
            put("owner", pid); put("ref_id", s.id); put("name", s.name); put("role", s.role.name.lowercase())
            put("fatigue", s.fatigue); put("skill", s.skill); put("status", s.status.name.lowercase())
            put("salary_per_day", s.salaryPerDay); put("rest_ticks", s.restTicks); put("morale", s.morale)
            put("assigned_vehicle", s.vehicleId)
        })
        for (c in world.contracts) upsert("contracts", c.id, pid, buildJsonObject {
            put("owner", pid); put("ref_id", c.id); put("type", c.type.name.lowercase()); put("status", c.status.name.lowercase())
            put("pickup", buildJsonObject { put("lat", c.pickup.lat); put("lng", c.pickup.lng); put("label", c.pickup.label); put("zone_id", c.pickup.zoneId) })
            put("dropoff", buildJsonObject { put("lat", c.dropoff.lat); put("lng", c.dropoff.lng); put("label", c.dropoff.label); put("zone_id", c.dropoff.zoneId) })
            put("payload", buildJsonObject { put("weight_kg", c.payload.weightKg); put("requires_refrigeration", c.payload.requiresRefrigeration) })
            put("reward", c.reward); put("deadline_tick", c.deadlineTick); put("created_tick", c.createdAtTick)
            put("completed_tick", c.completedAtTick); put("route_km", c.routeKm)
            put("assigned_vehicle", c.vehicleId); put("assigned_driver", c.driverId)
        })
        for (i in world.incidents) upsert("incidents", i.id, pid, buildJsonObject {
            put("owner", pid); put("ref_id", i.id); put("vehicle", i.vehicleId); put("driver", i.driverId)
            put("contract", i.contractId); put("zone_id", i.zoneId); put("type", i.type.name.lowercase())
            put("severity", i.severity); put("resolved", i.resolved); put("cost_if_ignored", i.costIfIgnored)
            put("description", i.description); put("created_tick", i.createdTick)
        })
        for (t in world.ledger) {
            if (!writtenTransactions.add("$pid:${t.id}")) continue
            upsert("transactions", t.id, pid, buildJsonObject {
                put("owner", pid); put("ref_id", t.id); put("tick", t.tick); put("category", t.category.name.lowercase())
                put("amount", t.amount); put("contract", t.contractId); put("description", t.description)
                put("idempotency_key", t.idempotencyKey)
            })
        }
        for (z in world.zones) upsert("city_state", z.zoneId, pid, buildJsonObject {
            put("owner", pid); put("ref_id", z.zoneId); put("zone_id", z.zoneId); put("name", z.name)
            put("demand_multiplier", z.demandMultiplier); put("traffic_index", z.trafficIndex)
            put("unlocked", z.unlocked); put("unlock_cost", z.unlockCost)
            put("completion_threshold", z.completionThreshold); put("completed_contracts", z.completed)
            put("failed_contracts", z.failed); put("unmet_demand", z.unmet)
        })
    }

    override suspend fun pendingIntents(): List<StoredIntent> {
        val res = get("/api/collections/intents/records?perPage=100&filter=" + urlEncode("status=\"pending\""))
        return res["items"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            StoredIntent(o["id"]!!.jsonPrimitive.content, o.str("owner"), o.str("idempotency_key"),
                o.str("type"), o["payload"]?.jsonObject ?: JsonObject(emptyMap()))
        } ?: emptyList()
    }

    override suspend fun finishIntent(intent: StoredIntent, accepted: Boolean, message: String) {
        write("PATCH", "/api/collections/intents/records/${intent.recordId}", buildJsonObject {
            put("status", if (accepted) "done" else "rejected"); put("result", message)
        }.toString())
    }

    override fun close() = client.close()
}

private const val XP_PER_CONTRACT = 120L
private const val XP_PER_LEVEL = 600L

private fun urlEncode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
private fun JsonObject.str(k: String): String = this[k]?.jsonPrimitive?.contentOrNull ?: ""
private fun JsonObject.num(k: String, d: Double): Double = this[k]?.jsonPrimitive?.doubleOrNull ?: d

class TickService(
    private val store: Store,
    private val routes: RouteProvider,
    private val engine: Engine,
    private val intervalMs: Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun start(scope: CoroutineScope): kotlinx.coroutines.Job = scope.launch {
        while (isActive) {
            val started = System.currentTimeMillis()
            var worlds = store.loadAll()
            if (worlds.isEmpty()) {
                val demo = City.initialWorld("demo")
                store.save(demo)
                worlds = listOf(demo)
            }
            for (world in worlds) {
                try {
                    // A freshly registered player has no fleet yet: seed it server-side.
                    var current = if (world.vehicles.isEmpty() && world.staff.isEmpty()) {
                        val seeded = City.initialWorld(world.playerId, world.companyName.ifBlank { "Apex City Logistics" },
                            world.seed, if (world.cash > 0) world.cash else 18500.0)
                        // A player with no fleet has never played: start the matching engine and clock.
                        seeded.copy(base = world.base, tick = world.tick, autoDispatch = true, paused = false,
                            speed = world.speed.coerceIn(1.0, 4.0))
                    } else world
                    val intents = store.pendingIntents().filter { it.playerId == world.playerId || it.playerId.isBlank() }
                    for (intent in intents) {
                        val outcome = try {
                            engine.apply(current, Intent(intent.key, intent.type, intent.payload), routes)
                        } catch (e: Exception) {
                            IntentOutcome(current, false, "Intent failed: ${e.message}")
                        }
                        current = outcome.world
                        store.finishIntent(intent, outcome.accepted, outcome.message)
                    }
                    repeat(current.speed.toInt().coerceIn(1, 4)) {
                        if (current.paused && !current.stepOnce) return@repeat
                        current = engine.step(current, routes).world
                    }
                    store.save(current)
                } catch (e: Exception) {
                    System.err.println("Tick failed for ${world.playerId}: ${e.message}")
                }
            }
            delay((intervalMs - (System.currentTimeMillis() - started)).coerceAtLeast(50))
        }
    }
}
