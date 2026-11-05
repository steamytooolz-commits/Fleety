package com.cityflow.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val osrm = System.getenv("OSRM_URL") ?: "http://localhost:5000"
    val pbUrl = System.getenv("POCKETBASE_URL") ?: "http://localhost:8090"
    val mode = System.getenv("SIM_MODE") ?: "local"
    val store: Store = if (mode == "pocketbase") {
        PocketBaseStore(pbUrl, System.getenv("PB_ADMIN_EMAIL") ?: "", System.getenv("PB_ADMIN_PASSWORD") ?: "")
    } else {
        MemoryStore(City.initialWorld("demo"))
    }
    val interval = System.getenv("TICK_INTERVAL_MS")?.toLongOrNull() ?: 2000L
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(store, OsrmRouteProvider(osrm), interval)
    }.start(wait = true)
}

fun Application.module(store: Store, routes: RouteProvider, intervalMs: Long = 2000L) {
    val engine = Engine()
    val json = Json { prettyPrint = false; encodeDefaults = true }
    install(ContentNegotiation) { json(json) }
    install(CallLogging)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val ticker = TickService(store, routes, engine, intervalMs)
    val job = ticker.start(scope)

    environment.monitor.subscribe(ApplicationStopped) {
        job.cancel()
        store.close()
    }

    routing {
        get("/health") {
            val worlds = store.loadAll()
            call.respond(buildJsonObject {
                put("status", "ok")
                put("mode", if (store is PocketBaseStore) "pocketbase" else "local")
                put("tick", worlds.firstOrNull()?.tick ?: 0)
                put("players", worlds.size)
                put("routing", "osrm")
            })
        }
        get("/api/state/{playerId}") {
            val id = call.parameters["playerId"]
            val world = store.loadAll().firstOrNull { it.playerId == id }
            if (world == null) call.respond(buildJsonObject { put("error", "unknown player") })
            else call.respond(world)
        }
        post("/api/expansion/simulate") {
            val body = call.receive<JsonObject>()
            val world = store.loadAll().firstOrNull()
                ?: return@post call.respond(buildJsonObject { put("error", "no world") })
            val request = ExpansionRequest(
                zoneId = body["zoneId"]?.toString()?.trim('"', ' '),
                additionalVehicles = body["additionalVehicles"]?.toString()?.toIntOrNull() ?: 0,
                additionalDrivers = body["additionalDrivers"]?.toString()?.toIntOrNull() ?: 0,
                marketingSpend = body["marketingSpend"]?.toString()?.toDoubleOrNull() ?: 0.0,
                days = body["days"]?.toString()?.toIntOrNull() ?: 30,
                iterations = body["iterations"]?.toString()?.toIntOrNull() ?: 500,
            )
            call.respond(MonteCarlo.run(world, request))
        }
    }
}
