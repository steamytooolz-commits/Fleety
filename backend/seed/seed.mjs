#!/usr/bin/env node
// Creates the demo player. The Kotlin tick service seeds fleet, zones and starting cash when it
// first loads a player with no vehicles.
const base = process.env.POCKETBASE_URL || 'http://localhost:8090';
const email = process.env.PB_ADMIN_EMAIL;
const password = process.env.PB_ADMIN_PASSWORD;
if (!email || !password) {
  console.error('Set PB_ADMIN_EMAIL and PB_ADMIN_PASSWORD (create the superuser with: pocketbase superuser upsert EMAIL PASS).');
  process.exit(1);
}

async function post(path, body, token) {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(token ? { authorization: token } : {}) },
    body: JSON.stringify(body),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`${path} ${res.status}: ${JSON.stringify(json)}`);
  return json;
}

const auth = await post('/api/collections/_superusers/auth-with-password', { identity: email, password });
const demo = {
  email: 'demo@cityflow.local',
  password: 'cityflow123',
  passwordConfirm: 'cityflow123',
  company_name: 'Apex City Logistics',
  verified: true,
  // A new operator starts with the matching engine on, a base and starting capital so the
  // first tick can seed a fleet instead of leaving the board idle.
  base_location: { lat: -33.9249, lng: 18.4241 },
  cash: 18500,
  level: 1,
  tick: 0,
  seed: 1337,
  speed: 1,
  auto_dispatch: true,
  paused: false,
};
try {
  const created = await post('/api/collections/players/records', demo, auth.token);
  console.log(`Created demo player ${created.id} (demo@cityflow.local / cityflow123)`);
} catch (e) {
  if (String(e.message).includes('400')) console.log('Demo player already exists.');
  else throw e;
}
