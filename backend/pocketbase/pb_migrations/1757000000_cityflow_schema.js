/// <reference path="../pb_data/types.d.ts" />

migrate(
  (app) => {
    const ownerRule = "owner = @request.auth.id";

    const players = new Collection({
      name: "players",
      type: "auth",
      listRule: "@request.auth.id != ''",
      viewRule: "id = @request.auth.id",
      createRule: "",
      updateRule: "id = @request.auth.id",
      deleteRule: "id = @request.auth.id",
      fields: [
        { name: "company_name", type: "text", max: 80 },
        { name: "base_location", type: "json", maxSize: 2000 },
        { name: "cash", type: "number", min: 0 },
        { name: "level", type: "number", min: 1 },
        { name: "tick", type: "number", min: 0 },
        { name: "seed", type: "number" },
        { name: "last_payroll_tick", type: "number", min: 0 },
        { name: "auto_dispatch", type: "bool" },
        { name: "paused", type: "bool" },
        { name: "speed", type: "number", min: 1, max: 4 },
      ],
    });
    app.save(players);

    const text = (name, required = false) => ({ name, type: "text", required });
    const num = (name, min = null, max = null) => ({ name, type: "number", min, max });

    const vehicles = new Collection({
      name: "vehicles",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: ownerRule,
      deleteRule: ownerRule,
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        text("ref_id", true),
        { name: "class", type: "select", values: ["sedan", "van", "truck", "refrigerated"], maxSelect: 1 },
        text("plate"),
        num("fuel", 0, 100),
        num("condition", 0, 100),
        num("mileage", 0),
        { name: "status", type: "select", maxSelect: 1, values: ["idle", "en_route_to_pickup", "at_pickup", "loading", "en_route_to_dropoff", "unloading", "divert_to_refuel", "breakdown", "forced_rest", "maintenance"] },
        { name: "current_location", type: "json", maxSize: 2000 },
        { name: "current_route", type: "json", maxSize: 200000 },
        num("route_progress", 0, 1),
        num("route_km", 0),
        num("distance_travelled_km", 0),
        num("low_fuel_ticks", 0),
        num("heading", 0, 360),
        text("assigned_driver"),
        text("contract"),
      ],
      indexes: ["CREATE UNIQUE INDEX `idx_vehicles_owner_ref` ON `vehicles` (`owner`, `ref_id`)"],
    });
    app.save(vehicles);

    app.save(new Collection({
      name: "staff",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: ownerRule,
      deleteRule: ownerRule,
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        text("ref_id", true), text("name"),
        { name: "role", type: "select", maxSelect: 1, values: ["driver", "dispatcher", "mechanic", "manager"] },
        num("fatigue", 0, 100), num("skill", 1, 10),
        { name: "status", type: "select", maxSelect: 1, values: ["idle", "on_shift", "resting", "fired"] },
        num("salary_per_day", 0), num("rest_ticks", 0), num("morale", 0, 100),
        text("assigned_vehicle"),
      ],
      indexes: ["CREATE UNIQUE INDEX `idx_staff_owner_ref` ON `staff` (`owner`, `ref_id`)"],
    }));

    app.save(new Collection({
      name: "contracts",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: ownerRule,
      deleteRule: ownerRule,
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        text("ref_id", true),
        { name: "type", type: "select", maxSelect: 1, values: ["rideshare", "food", "parcel", "cargo"] },
        { name: "pickup", type: "json", maxSize: 5000 },
        { name: "dropoff", type: "json", maxSize: 5000 },
        { name: "payload", type: "json", maxSize: 5000 },
        num("reward", 0),
        num("deadline_tick", 0),
        { name: "status", type: "select", maxSelect: 1, values: ["open", "assigned", "in_progress", "completed", "failed"] },
        text("assigned_vehicle"), text("assigned_driver"),
        num("created_tick", 0), num("completed_tick", 0), num("route_km", 0),
      ],
      indexes: ["CREATE UNIQUE INDEX `idx_contracts_owner_ref` ON `contracts` (`owner`, `ref_id`)"],
    }));

    app.save(new Collection({
      name: "incidents",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: ownerRule,
      deleteRule: ownerRule,
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        text("ref_id", true), text("vehicle"), text("driver"), text("contract"), text("zone_id"),
        { name: "type", type: "select", maxSelect: 1, values: ["low_fuel", "breakdown", "accident", "strike", "traffic_jam", "fatigue"] },
        num("severity", 1, 3),
        { name: "resolved", type: "bool" },
        num("cost_if_ignored", 0),
        text("description"), num("created_tick", 0),
      ],
      indexes: ["CREATE UNIQUE INDEX `idx_incidents_owner_ref` ON `incidents` (`owner`, `ref_id`)"],
    }));

    app.save(new Collection({
      name: "transactions",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: "",
      deleteRule: "",
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        text("ref_id"), num("tick", 0),
        { name: "category", type: "select", maxSelect: 1, values: ["revenue", "fuel", "salary", "repair", "tow", "expansion", "purchase", "penalty", "loan"] },
        num("amount"), text("contract"), text("description"), text("idempotency_key"),
      ],
      indexes: ["CREATE UNIQUE INDEX `idx_transactions_owner_ref` ON `transactions` (`owner`, `ref_id`)"],
    }));

    app.save(new Collection({
      name: "city_state",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: ownerRule,
      deleteRule: ownerRule,
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        text("ref_id"), text("zone_id"), text("name"),
        num("demand_multiplier", 0), num("traffic_index", 0, 1),
        { name: "unlocked", type: "bool" },
        num("unlock_cost", 0), num("completion_threshold", 0, 1),
        num("completed_contracts", 0), num("failed_contracts", 0), num("unmet_demand", 0),
        { name: "unlocked_services", type: "json", maxSize: 5000 },
      ],
      indexes: ["CREATE UNIQUE INDEX `idx_city_owner_zone` ON `city_state` (`owner`, `zone_id`)"],
    }));

    app.save(new Collection({
      name: "intents",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: ownerRule,
      deleteRule: "",
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        text("idempotency_key", true),
        { name: "type", type: "text", required: true },
        { name: "payload", type: "json", maxSize: 10000 },
        { name: "status", type: "select", maxSelect: 1, values: ["pending", "done", "rejected"] },
        text("result"),
      ],
      indexes: ["CREATE UNIQUE INDEX `idx_intents_owner_key` ON `intents` (`owner`, `idempotency_key`)"],
    }));
  },
  (app) => {
    for (const name of ["intents", "city_state", "transactions", "incidents", "contracts", "staff", "vehicles", "players"]) {
      try { app.delete(app.findCollectionByNameOrId(name)); } catch (_) { /* already gone */ }
    }
  }
);
