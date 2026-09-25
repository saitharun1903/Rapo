// Nearby search under load: passengers look for cars around random points in the city while a fleet of
// online drivers keeps reporting positions in the background, as the apps do. Search reads the positions
// that reached PostgreSQL through Kafka and only returns drivers seen in the last 30 s.
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';
import {
  BASE_URL, DURATION, MEASURED, SETUP_OPTIONS, VUS, authed, createAccounts, createOnlineDrivers, loginAll,
  randomPointWithin, reportLocation, reportThresholds, summaryFile, takeOffline,
} from './lib.js';

const DRIVERS = Number(__ENV.NEARBY_DRIVERS || 30);
/** Drivers are spread over the central city, where passengers search. */
const AREA_RADIUS_METERS = 5000;
const SEARCH_RADIUS_METERS = 3000;
/** Each driver reports every few seconds, well inside the 30 s freshness window. */
const REPORT_EVERY = '5s';
const HEARTBEAT_VUS = 10;

const driversFound = new Trend('nearby_drivers_found');

export const options = {
  ...SETUP_OPTIONS,
  scenarios: {
    [MEASURED]: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'search' },
    heartbeat: {
      executor: 'constant-arrival-rate', rate: DRIVERS, timeUnit: REPORT_EVERY, duration: DURATION,
      preAllocatedVUs: HEARTBEAT_VUS, exec: 'heartbeat',
    },
  },
  thresholds: reportThresholds(['nearby']),
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const positions = [...Array(DRIVERS).keys()].map(() => randomPointWithin(AREA_RADIUS_METERS));
  return {
    drivers: createOnlineDrivers(positions, 'nearby'),
    passengers: loginAll(createAccounts(VUS, 'PASSENGER', 'search')),
  };
}

export function heartbeat(data) {
  const driver = data.drivers[exec.scenario.iterationInTest % data.drivers.length];
  reportLocation(driver, driver.position, 'location');
}

export function search(data) {
  const passenger = data.passengers[(exec.vu.idInTest - 1) % data.passengers.length];
  const point = randomPointWithin(AREA_RADIUS_METERS);
  const response = http.get(`${BASE_URL}/api/drivers/nearby?lat=${point.lat}&lng=${point.lng}`
    + `&radiusMeters=${SEARCH_RADIUS_METERS}&category=ECONOMY`, authed(passenger.token, 'nearby'));
  if (check(response, { 'nearby 200': (r) => r.status === 200 })) {
    driversFound.add(response.json().length);
  }
}

export function teardown(data) {
  takeOffline(data.drivers);
}

export function handleSummary(summary) {
  return summaryFile('nearby', summary);
}
