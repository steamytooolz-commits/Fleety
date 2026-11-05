# CityFlow

City logistics management simulation for Android. The client renders server-owned state on a
MapLibre map and submits operator intents; a Kotlin Ktor service owns the simulation, PocketBase
stores the authoritative player state and realtime deltas, and OSRM supplies road geometry.

## Architecture

| Path | Purpose |
| --- | --- |
| `app/` | Android client: Compose UI, MapLibre map, PocketBase auth/realtime/intents. |
| `server/` | Authoritative tick service: FSM, fuel, fatigue, incidents, payroll, demand, matching, Monte Carlo. |
| `backend/docker-compose.yml` | PocketBase 0.40.6 (built from the official release) + OSRM (MLD). |
| `backend/pocketbase/pb_migrations/` | Collections, owner-scoped API rules, unique idempotency index. |
| `backend/seed/seed.mjs` | Creates the demo player. |
| `.github/workflows/` | `android-debug.yml` builds the APK; `server-tests.yml` runs simulation tests. |

The tick service is authoritative. The app never advances time or edits simulation state: it reads
PocketBase snapshots and SSE deltas, interpolates markers, and POSTs intents (assign, purchase,
refuel, hire, shift, unlock, pause, speed, step, resolve, spawn). Every intent has an idempotency key
and is validated against the server's world before it is applied.

## Quick start

### 1. Backend

```bash
mkdir -p backend/osrm/data
curl -L -o backend/osrm/data/south-africa-latest.osm.pbf \
  https://download.geofabrik.de/africa/south-africa-latest.osm.pbf
cd backend && docker compose up -d --build
```

OSRM extraction happens once in the `osrm-prepare` container; the routed service then listens on
`http://localhost:5000`. Create an admin and the demo player:

```bash
docker compose exec pocketbase /pb/pocketbase superuser upsert admin@example.com 'change-me-now'
PB_ADMIN_EMAIL=admin@example.com PB_ADMIN_PASSWORD='change-me-now' node seed/seed.mjs
```

PocketBase runs at `http://localhost:8090` with the migrations mounted from
`backend/pocketbase/pb_migrations`. Fresh databases apply them automatically.

### 2. Tick service

```bash
cd server
SIM_MODE=pocketbase \
POCKETBASE_URL=http://localhost:8090 \
PB_ADMIN_EMAIL=admin@example.com \
PB_ADMIN_PASSWORD='change-me-now' \
OSRM_URL=http://localhost:5000 \
PORT=8080 gradle run
```

The service authenticates as superuser, seeds a newly registered player's fleet, advances one tick
per `TICK_INTERVAL_MS` (default 2000 ms; 1 tick = 1 simulated minute), writes all collections, and
consumes pending intents. `SIM_MODE=local` runs one in-memory world without PocketBase.

Health: `GET /health`, snapshot: `GET /api/state/{playerId}`, projection:
`POST /api/expansion/simulate`.

### 3. Android

The client defaults to the emulator host `http://10.0.2.2:8090` for PocketBase and
`http://10.0.2.2:5000` for OSRM, and signs in / registers the demo player
`demo@cityflow.local` / `cityflow123` automatically. On a physical device, open **Settings** in the
app and set the PocketBase and OSRM URLs to the machine running the backend.

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Cleartext HTTP is enabled for local development. Map tiles come from OpenStreetMap and the map shows
`© OpenStreetMap contributors`.

## Simulation rules

- **Movement**: one tick = one minute. Vehicles move along the OSRM polyline; no straight-line
  fallback. Routes come from `GET /route/v1/driving/{lng},{lat};...`.
- **Fuel**: `base × (1 + payload_kg × 0.0005) × (1 + traffic × 0.1) / skill`. Below 15% opens a
  severity-2 alert; three ignored ticks break the vehicle down (tow cost + severity-3 incident).
  Condition below 20% breaks down immediately.
- **Fatigue**: driving adds 0.45/tick, idle 0.15/tick. Above 80 the driver is forced to rest, the
  vehicle is released and the contract reopens.
- **Payroll**: every 30 ticks. An unpayable payroll clamps cash at zero, cuts morale and starts a
  strike.
- **Matching**: pure, order-independent; weighted proximity/ETA/suitability/skill/fuel minus fatigue
  and congestion, threshold 0.35, tie-break lowest driver id then vehicle id. Cargo above 500 kg
  needs a truck; chilled food needs the refrigerated van; sedans cannot carry cargo.
- **Demand**: seeded per-zone Poisson processes over Cape Town POIs. No randomness outside the
  seeded demand stream.
- **History**: active + last 200 closed contracts, last 40 resolved incidents and 400 ledger rows are
  retained to bound sync cost.

## Verification

Run here and passing:

- `cd server && gradle test` — 8 tests, 0 failures: polyline adherence, order-independent deterministic
  matching, suitability rejection, low-fuel breakdown + tow, payroll clamp/strike, duplicate intent
  rejection, Monte Carlo reproducibility/percentiles.
- Live PocketBase mode with a mock OSRM: 2 vehicles, 3 staff, 5 zones seeded; 19 contracts delivered,
  revenue and payroll ledger rows written; vehicle route geometry persisted.
- Real intent round-trip through PocketBase: `SET_PAUSED` froze the tick (155 → 155), `FORCE_STEP`
  advanced exactly one (156), `SET_PAUSED false` resumed; all three intents reported `done` with
  results.
- PocketBase realtime SSE handshake with the same flow the app uses: `PB_CONNECT` → subscribed to
  `vehicles`/`players` → received live vehicle status and player-tick deltas.
- Server restart resumed from persisted tick (1100+) and continued saving without duplicate-record
  errors.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — APK built (65 MB with MapLibre native libs),
  unit tests pass.
- `docker compose config` validates; the pinned PocketBase 0.40.6 release asset responds 200.
- Both workflow YAML files parse.

Not verified in this environment:

- On-device/emulator rendering and interaction: MapLibre style/tiles, marker tap selection,
  interpolation and the 150-vehicle target have not been visually exercised (no emulator here).
- Full `docker compose up` with the South Africa PBF and real OSRM extraction; the tested OSRM was a
  deterministic mock that returns GeoJSON routes.
- GitHub Actions execution (nothing has been pushed). The keystore and Gradle setup were reproduced
  locally with the same command.

## Security notes

- No superuser credentials exist in the APK. The client uses the ordinary demo player account and
  owner-scoped PocketBase rules (`owner = @request.auth.id`).
- The server needs superuser credentials at runtime; pass them as environment variables, never commit
  them.
- `users`/`_superusers` are untouched; player records are the auth collection used by the game.
- Cleartext traffic is for local development only; use HTTPS and a reverse proxy for a hosted
  deployment.
