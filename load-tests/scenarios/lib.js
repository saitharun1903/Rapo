// Shared helpers for the Phase 12 scenario load tests: configuration, account creation through the public
// API (the same calls the apps make), and points inside the service area.
import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const VUS = Number(__ENV.VUS || 10);
export const DURATION = __ENV.DURATION || '60s';
export const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
export const RESULTS_DIR = __ENV.RESULTS_DIR || 'results/scenarios';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL;
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD;

// Accounts made by the test; a fresh password per run, never stored.
const PASSWORD = `Load-${RUN_ID}-pass1`;
// Registration and login hash with BCrypt (cost 12), so setup creates accounts in parallel batches.
const SETUP_BATCH = 20;
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const EARTH_METERS_PER_DEGREE_LAT = 111_320;

/** The demo city's service area (backend defaults: Hyderabad, 40 km). */
export const CENTER = { lat: 17.385, lng: 78.4867 };

/** Setup and teardown create a few hundred accounts; allow them time, BCrypt included. */
export const SETUP_OPTIONS = { setupTimeout: '10m', teardownTimeout: '5m' };

/**
 * The measured scenario is always called "load"; setup, teardown and background traffic are not in its
 * numbers.
 */
export const MEASURED = 'load';

// Always-true thresholds: they only make k6 put these sub-metrics in the summary, for the report.
export function reportThresholds(names) {
  const thresholds = {
    [`http_reqs{scenario:${MEASURED}}`]: ['count>=0'],
    [`http_req_failed{scenario:${MEASURED}}`]: ['rate>=0'],
    [`iterations{scenario:${MEASURED}}`]: ['count>=0'],
    [`checks{scenario:${MEASURED}}`]: ['rate>=0'],
  };
  for (const name of names) {
    thresholds[`http_req_duration{name:${name}}`] = ['max>=0'];
    thresholds[`http_req_failed{name:${name}}`] = ['rate>=0'];
  }
  return thresholds;
}

export function offset(point, northMeters, eastMeters) {
  const metersPerDegreeLng = EARTH_METERS_PER_DEGREE_LAT * Math.cos((point.lat * Math.PI) / 180);
  return {
    lat: point.lat + northMeters / EARTH_METERS_PER_DEGREE_LAT,
    lng: point.lng + eastMeters / metersPerDegreeLng,
  };
}

export function randomPointWithin(radiusMeters) {
  const distance = radiusMeters * Math.sqrt(Math.random());
  const angle = 2 * Math.PI * Math.random();
  return offset(CENTER, distance * Math.cos(angle), distance * Math.sin(angle));
}

export function authed(token, name) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { name } };
}

export function post(path, body, token, name) {
  return http.post(`${BASE_URL}${path}`, body === null ? null : JSON.stringify(body), authed(token, name));
}

function expect(response, status, what) {
  if (response.status !== status) {
    fail(`${what}: expected ${status}, got ${response.status} ${response.body}`);
  }
  return response;
}

function batches(count, build) {
  const results = [];
  for (let start = 0; start < count; start += SETUP_BATCH) {
    const indexes = [...Array(Math.min(SETUP_BATCH, count - start)).keys()].map((i) => start + i);
    const responses = http.batch(indexes.map((i) => build(i)));
    results.push(...responses);
  }
  return results;
}

/** Registers `count` accounts of `accountType` and returns their emails and access tokens. */
export function createAccounts(count, accountType, label) {
  const emails = [...Array(count).keys()].map((i) => `load-${label}-${RUN_ID}-${i}@example.com`);
  batches(count, (i) => ['POST', `${BASE_URL}/api/auth/register`, JSON.stringify({
    email: emails[i], password: PASSWORD, fullName: `Load ${label} ${i}`, accountType,
  }), { headers: JSON_HEADERS, tags: { name: 'setup' } }])
    .forEach((response, i) => expect(response, 201, `register ${emails[i]}`));
  return emails.map((email) => ({ email, token: null }));
}

export function login(email, name) {
  return http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { name } });
}

export function loginAll(accounts) {
  const responses = batches(accounts.length, (i) => ['POST', `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: accounts[i].email, password: PASSWORD }), { headers: JSON_HEADERS, tags: { name: 'setup' } }]);
  return accounts.map((account, i) => ({ ...account, token: expect(responses[i], 200, `login ${account.email}`).json('accessToken') }));
}

function adminToken() {
  if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
    fail('ADMIN_EMAIL and ADMIN_PASSWORD are needed to verify the test drivers');
  }
  return expect(http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } }), 200, 'admin login').json('accessToken');
}

/**
 * Drivers ready to take rides: registered, onboarded with an ECONOMY vehicle, verified by the admin and
 * online at `positions[i]`.
 */
export function createOnlineDrivers(positions, label) {
  const drivers = loginAll(createAccounts(positions.length, 'DRIVER', label));
  const profiles = batches(drivers.length, (i) => ['POST', `${BASE_URL}/api/drivers/me/profile`, JSON.stringify({
    licenseNumber: `LT-${RUN_ID}-${label}-${i}`.slice(0, 40),
    vehicle: {
      make: 'Maruti', model: 'Dzire', color: 'White', modelYear: 2022, seats: 4, category: 'ECONOMY',
      plateNumber: `LT${String(RUN_ID).slice(-6)}${label.slice(0, 2).toUpperCase()}${i}`.slice(0, 20),
    },
  }), authed(drivers[i].token, 'setup')]);
  const admin = adminToken();
  const verified = batches(drivers.length, (i) => ['POST',
    `${BASE_URL}/api/admin/drivers/${expect(profiles[i], 201, `profile ${drivers[i].email}`).json('id')}/verify`,
    null, authed(admin, 'setup')]);
  verified.forEach((response, i) => expect(response, 200, `verify ${drivers[i].email}`));
  batches(drivers.length, (i) => ['POST', `${BASE_URL}/api/drivers/online`,
    JSON.stringify({ location: positions[i] }), authed(drivers[i].token, 'setup')])
    .forEach((response, i) => expect(response, 200, `online ${drivers[i].email}`));
  return drivers.map((driver, i) => ({ ...driver, position: positions[i] }));
}

/** Best effort: a driver still on a trip cannot go offline, and the presence sweeper catches the rest. */
export function takeOffline(drivers) {
  batches(drivers.length, (i) => ['POST', `${BASE_URL}/api/drivers/offline`, null, authed(drivers[i].token, 'teardown')]);
}

export function reportLocation(driver, point, name) {
  const response = post('/api/drivers/location', { location: point, recordedAt: new Date().toISOString() },
    driver.token, name);
  check(response, { 'location accepted': (r) => r.status === 202 });
  return response;
}

export function place(point, label) {
  return { point, address: label };
}

/** k6's summary as JSON, for report.py. */
export function summaryFile(scenario, summary) {
  return { [`${RESULTS_DIR}/summary-${scenario}-${VUS}.json`]: JSON.stringify(summary, null, 2) };
}
