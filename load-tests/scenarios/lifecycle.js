// The whole ride, over and over: each VU is one passenger and one driver. The passenger books; matching
// (through Kafka) offers the ride to the driver, who polls their offers like the REST fallback of the app,
// accepts, drives to the pickup, starts, drives to the dropoff and completes; the next ride starts from the
// driver's grid cell again. Drivers wait on a grid wider than the first matching radius, so round one offers
// each ride only to its own driver.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  BASE_URL, CENTER, DURATION, MEASURED, SETUP_OPTIONS, VUS, authed, createAccounts, createOnlineDrivers, loginAll,
  offset, place, post, reportLocation, reportThresholds, summaryFile, takeOffline,
} from './lib.js';

/** Wider than the first matching round's radius (3000 m). */
const GRID_SPACING_METERS = 3200;
/** Keeps pickups and dropoffs inside the 40 km service area. */
const GRID_RADIUS_METERS = 30000;
/** Earlier runs in the same job leave their drivers on the first cells; each run starts after them. */
const CELL_OFFSET = Number(__ENV.CELL_OFFSET || 0);
const TRIP_NORTH_METERS = 2000;
const OFFER_WAIT_MS = 30000;
const OFFER_POLL_SECONDS = 0.25;
const CATEGORY = 'ECONOMY';

const timeToOffer = new Trend('time_to_offer', true);
const rideDuration = new Trend('ride_lifecycle', true);
const ridesCompleted = new Counter('rides_completed');
const offersMissed = new Counter('offers_missed');

const STEPS = ['location', 'estimate', 'book', 'offers', 'accept', 'en-route', 'arrive', 'start', 'complete'];

export const options = {
  ...SETUP_OPTIONS,
  scenarios: {
    // A ride in progress when time is up may finish.
    [MEASURED]: { executor: 'constant-vus', vus: VUS, duration: DURATION, gracefulStop: '60s' },
  },
  thresholds: reportThresholds(STEPS),
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

/** Grid cells inside the service area, nearest to the centre first. */
function cells() {
  const steps = Math.floor(GRID_RADIUS_METERS / GRID_SPACING_METERS);
  const found = [];
  for (let north = -steps; north <= steps; north++) {
    for (let east = -steps; east <= steps; east++) {
      const distance = Math.hypot(north, east) * GRID_SPACING_METERS;
      if (distance <= GRID_RADIUS_METERS - TRIP_NORTH_METERS) {
        found.push({ distance, point: offset(CENTER, north * GRID_SPACING_METERS, east * GRID_SPACING_METERS) });
      }
    }
  }
  return found.sort((a, b) => a.distance - b.distance).map((cell) => cell.point);
}

export function setup() {
  const grid = cells();
  if (CELL_OFFSET + VUS > grid.length) {
    throw new Error(`need ${CELL_OFFSET + VUS} grid cells, the service area holds ${grid.length}`);
  }
  return {
    drivers: createOnlineDrivers(grid.slice(CELL_OFFSET, CELL_OFFSET + VUS), 'lifecycle'),
    passengers: loginAll(createAccounts(VUS, 'PASSENGER', 'rider')),
  };
}

function step(response, name, status) {
  return check(response, { [`${name} ${status}`]: (r) => r.status === status });
}

function awaitOffer(driver, rideId) {
  const deadline = Date.now() + OFFER_WAIT_MS;
  while (Date.now() < deadline) {
    const offers = http.get(`${BASE_URL}/api/drivers/me/offers`, authed(driver.token, 'offers'));
    if (offers.status === 200 && offers.json().some((offer) => offer.rideId === rideId)) {
      return true;
    }
    sleep(OFFER_POLL_SECONDS);
  }
  return false;
}

export default function (data) {
  const driver = data.drivers[(__VU - 1) % data.drivers.length];
  const passenger = data.passengers[(__VU - 1) % data.passengers.length];
  const pickup = driver.position;
  const dropoff = offset(pickup, TRIP_NORTH_METERS, 0);

  // A fresh position, so matching sees the driver (positions older than 30 s are ignored).
  reportLocation(driver, pickup, 'location');
  const estimate = post('/api/fares/estimate', { pickup, dropoff }, passenger.token, 'estimate');
  if (!step(estimate, 'estimate', 200)) {
    return;
  }
  const quote = estimate.json('quotes').find((q) => q.vehicleCategory === CATEGORY);
  const bookedAt = Date.now();
  const booked = post('/api/rides', {
    quoteId: quote.quoteId, pickup: place(pickup, 'Load test pickup'), dropoff: place(dropoff, 'Load test dropoff'),
    paymentMethod: 'CASH',
  }, passenger.token, 'book');
  if (!step(booked, 'book', 201)) {
    return;
  }
  const rideId = booked.json('id');
  const ride = `/api/rides/${rideId}`;

  if (!awaitOffer(driver, rideId)) {
    offersMissed.add(1);
    check(false, { 'offer received': (received) => received });
    post(`${ride}/cancel`, { reason: 'Load test: no offer' }, passenger.token, 'cancel');
    return;
  }
  timeToOffer.add(Date.now() - bookedAt);

  if (!step(post(`${ride}/accept`, null, driver.token, 'accept'), 'accept', 200)
      || !step(post(`${ride}/en-route`, null, driver.token, 'en-route'), 'en-route', 200)) {
    return;
  }
  reportLocation(driver, pickup, 'location');
  if (!step(post(`${ride}/arrive`, null, driver.token, 'arrive'), 'arrive', 200)
      || !step(post(`${ride}/start`, null, driver.token, 'start'), 'start', 200)) {
    return;
  }
  reportLocation(driver, dropoff, 'location');
  if (step(post(`${ride}/complete`, null, driver.token, 'complete'), 'complete', 200)) {
    rideDuration.add(Date.now() - bookedAt);
    ridesCompleted.add(1);
  }
}

export function teardown(data) {
  takeOffline(data.drivers);
}

export function handleSummary(summary) {
  return summaryFile('lifecycle', summary);
}
