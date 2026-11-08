/// <reference path="../pb_data/types.d.ts" />

/**
 * CityFlow schema v3 — deeper operations.
 *
 * Six new systems share the existing server-authoritative world:
 *  - freight tiers on contracts (standard / express / bulk),
 *  - workshop time on vehicles (condition was already tracked),
 *  - a fuel market: pump price plus a paid depot reserve on the player,
 *  - marketing campaigns (own collection, owner-scoped like every other state),
 *  - operating credit: limit, terms and balance on the player,
 *  - city events with speed/demand/reward modifiers on the player.
 */
migrate(
  (app) => {
    const players = app.findCollectionByNameOrId("players");
    players.fields.add(new NumberField({ name: "fuel_price", min: 0.9, max: 1.6, required: false }));
    players.fields.add(new NumberField({ name: "fuel_reserve", min: 0, required: false }));
    players.fields.add(new NumberField({ name: "credit_limit", min: 0, required: false }));
    players.fields.add(new JSONField({ name: "loan", maxSize: 4000, required: false }));
    players.fields.add(new JSONField({ name: "active_event", maxSize: 4000, required: false }));
    app.save(players);

    const vehicles = app.findCollectionByNameOrId("vehicles");
    vehicles.fields.add(new NumberField({ name: "service_ticks", min: 0, required: false }));
    app.save(vehicles);

    const contracts = app.findCollectionByNameOrId("contracts");
    contracts.fields.add(
      new SelectField({ name: "tier", maxSelect: 1, values: ["standard", "express", "bulk"], required: false })
    );
    contracts.indexes.push(
      "CREATE INDEX `idx_contracts_owner_tier` ON `contracts` (`owner`, `tier`)"
    );
    app.save(contracts);

    const ownerRule = "owner = @request.auth.id";
    app.save(new Collection({
      name: "campaigns",
      type: "base",
      listRule: ownerRule,
      viewRule: ownerRule,
      createRule: ownerRule,
      updateRule: ownerRule,
      deleteRule: ownerRule,
      fields: [
        { name: "owner", type: "relation", required: true, collectionId: players.id, maxSelect: 1, cascadeDelete: true },
        { name: "ref_id", type: "text", required: true },
        { name: "zone_id", type: "text", required: true },
        { name: "ticks_left", type: "number", min: 0 },
        { name: "spend", type: "number", min: 0 },
        { name: "demand_boost", type: "number", min: 0 },
        { name: "reward_boost", type: "number", min: 0 },
      ],
      indexes: [
        "CREATE UNIQUE INDEX `idx_campaigns_owner_ref` ON `campaigns` (`owner`, `ref_id`)",
        "CREATE INDEX `idx_campaigns_owner_zone` ON `campaigns` (`owner`, `zone_id`)",
      ],
    }));
  },
  (app) => {
    const players = app.findCollectionByNameOrId("players");
    for (const field of ["fuel_price", "fuel_reserve", "credit_limit", "loan", "active_event"]) {
      try {
        players.fields.removeByName(field);
      } catch (_) {
        /* field already gone */
      }
    }
    app.save(players);

    const vehicles = app.findCollectionByNameOrId("vehicles");
    try {
      vehicles.fields.removeByName("service_ticks");
    } catch (_) {
      /* field already gone */
    }
    app.save(vehicles);

    const contracts = app.findCollectionByNameOrId("contracts");
    try {
      contracts.fields.removeByName("tier");
    } catch (_) {
      /* field already gone */
    }
    contracts.indexes = contracts.indexes.filter((index) => !index.includes("idx_contracts_owner_tier"));
    app.save(contracts);

    try {
      app.delete(app.findCollectionByNameOrId("campaigns"));
    } catch (_) {
      /* collection already gone */
    }
  }
);
