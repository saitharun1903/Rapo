// Before/after benchmark for the Redis caches (Phase 5): fare estimates and place search, run once with
// CACHE_ENABLED=false and once with CACHE_ENABLED=true against the same backend build.
// Run by .github/workflows/cache-benchmark.yml; see load-tests/README.md for running it locally.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || 'unspecified';
const VUS = Number(__ENV.VUS || 20);
const SEARCH_VUS = Math.max(1, Math.floor(VUS / 4));
const DURATION = __ENV.DURATION || '60s';
const PASSWORD = 'Benchmark-pass-1';

// Well-known Hyderabad landmarks. Passengers mostly pick popular places from search results, so the
// same pickup/dropoff pairs recur; 12 places give 132 ordered trips.
const PLACES = [
  [17.4435, 78.3772], // HITEC City
  [17.4239, 78.4738], // Hussain Sagar
  [17.3616, 78.4747], // Charminar
  [17.3833, 78.4011], // Golconda Fort
  [17.4401, 78.4983], // Secunderabad station
  [17.4126, 78.4482], // Banjara Hills
  [17.4325, 78.4071], // Jubilee Hills
  [17.4948, 78.3996], // Kukatpally
  [17.4483, 78.3915], // Madhapur
  [17.3713, 78.4804], // Mozamjahi Market
  [17.4065, 78.4772], // Lakdikapul
  [17.4399, 78.3489], // Gachibowli
];
const QUERIES = ['charminar', 'golconda fort', 'hitec city', 'hussain sagar', 'banjara hills', 'jubilee hills',
  'kukatpally', 'gachibowli', 'secunderabad station', 'madhapur', 'lakdikapul', 'begumpet'];

export const options = {
  scenarios: {
    estimates: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'estimate' },
    search: { executor: 'constant-vus', vus: SEARCH_VUS, duration: DURATION, exec: 'search' },
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  // Always-true thresholds, only so per-scenario sub-metrics appear in the summary.
  thresholds: {
    'http_req_duration{scenario:estimates}': ['max>=0'],
    'http_req_duration{scenario:search}': ['max>=0'],
    'http_reqs{scenario:estimates}': ['count>=0'],
    'http_reqs{scenario:search}': ['count>=0'],
    'http_req_failed{scenario:estimates}': ['rate>=0'],
    'http_req_failed{scenario:search}': ['rate>=0'],
  },
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export function setup() {
  const tokens = [];
  for (let i = 0; i < VUS + SEARCH_VUS; i++) {
    const email = `bench-${MODE}-${Date.now()}-${i}@example.com`;
    const reg = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
      email, password: PASSWORD, fullName: `Benchmark ${i}`, accountType: 'PASSENGER',
    }), JSON_HEADERS);
    if (reg.status !== 201) {
      throw new Error(`register failed: ${reg.status} ${reg.body}`);
    }
    const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }), JSON_HEADERS);
    if (login.status !== 200) {
      throw new Error(`login failed: ${login.status} ${login.body}`);
    }
    tokens.push(login.json('accessToken'));
  }
  return { tokens };
}

function auth(data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function estimate(data) {
  const from = pick(PLACES);
  let to = pick(PLACES);
  while (to === from) {
    to = pick(PLACES);
  }
  const res = http.post(`${BASE_URL}/api/fares/estimate`, JSON.stringify({
    pickup: { lat: from[0], lng: from[1] }, dropoff: { lat: to[0], lng: to[1] },
  }), auth(data));
  check(res, { 'estimate 200': (r) => r.status === 200 });
}

export function search(data) {
  const res = http.get(`${BASE_URL}/api/geo/search?q=${encodeURIComponent(pick(QUERIES))}`, auth(data));
  check(res, { 'search 200': (r) => r.status === 200 });
}

export function handleSummary(summary) {
  return { [`results/summary-${MODE}.json`]: JSON.stringify(summary, null, 2) };
}
