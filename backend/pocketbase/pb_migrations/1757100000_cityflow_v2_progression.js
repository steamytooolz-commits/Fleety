/// <reference path="../pb_data/types.d.ts" />

/**
 * CityFlow schema v2 — operator progression, faster dashboard reads and tighter intent rules.
 *
 * v1 kept only the live simulation state. The console now shows a career view (level, reputation,
 * lifetime revenue, distance) and paginates the ledger, so the player record carries lifetime
 * counters that survive the windowed contract/transaction collections, and the hot filters get
 * covering indexes.
 */
migrate(
  (app) => {
    const players = app.findCollectionByNameOrId("players");
    players.fields.add(new NumberField({ name: "xp", min: 0, required: false }));
    players.fields.add(new NumberField({ name: "reputation", min: 0, max: 100, required: false }));
    players.fields.add(new NumberField({ name: "total_revenue", min: 0, required: false }));
    players.fields.add(new NumberField({ name: "contracts_completed", min: 0, required: false }));
    players.fields.add(new NumberField({ name: "contracts_failed", min: 0, required: false }));
    players.fields.add(new NumberField({ name: "distance_km", min: 0, required: false }));
    app.save(players);

    const transactions = app.findCollectionByNameOrId("transactions");
    transactions.indexes.push(
      "CREATE INDEX `idx_transactions_owner_tick` ON `transactions` (`owner`, `tick`)"
    );
    transactions.indexes.push(
      "CREATE INDEX `idx_transactions_owner_category` ON `transactions` (`owner`, `category`)"
    );
    app.save(transactions);

    const contracts = app.findCollectionByNameOrId("contracts");
    contracts.indexes.push(
      "CREATE INDEX `idx_contracts_owner_status` ON `contracts` (`owner`, `status`)"
    );
    contracts.indexes.push(
      "CREATE INDEX `idx_contracts_owner_deadline` ON `contracts` (`owner`, `deadline_tick`)"
    );
    app.save(contracts);

    const incidents = app.findCollectionByNameOrId("incidents");
    incidents.indexes.push(
      "CREATE INDEX `idx_incidents_owner_open` ON `incidents` (`owner`, `resolved`, `severity`)"
    );
    app.save(incidents);

    // Intents are a client -> server queue. Only the superuser (tick service) may resolve them,
    // otherwise a player could mark their own intents "done" without the simulation applying them.
    const intents = app.findCollectionByNameOrId("intents");
    intents.updateRule = "";
    intents.deleteRule = "";
    app.save(intents);
  },
  (app) => {
    const players = app.findCollectionByNameOrId("players");
    for (const field of ["xp", "reputation", "total_revenue", "contracts_completed", "contracts_failed", "distance_km"]) {
      try {
        players.fields.removeByName(field);
      } catch (_) {
        /* field already gone */
      }
    }
    app.save(players);

    const dropIndexes = (collection, names) => {
      collection.indexes = collection.indexes.filter((index) => !names.some((name) => index.includes(name)));
      app.save(collection);
    };
    dropIndexes(app.findCollectionByNameOrId("transactions"), [
      "idx_transactions_owner_tick",
      "idx_transactions_owner_category",
    ]);
    dropIndexes(app.findCollectionByNameOrId("contracts"), [
      "idx_contracts_owner_status",
      "idx_contracts_owner_deadline",
    ]);
    dropIndexes(app.findCollectionByNameOrId("incidents"), ["idx_incidents_owner_open"]);

    const intents = app.findCollectionByNameOrId("intents");
    intents.updateRule = "owner = @request.auth.id";
    app.save(intents);
  }
);
