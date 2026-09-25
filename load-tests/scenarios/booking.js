// Ride creation under load: each VU is a passenger who gets a fare quote, books with it and cancels while
// the ride is still matching (a passenger can have only one active ride). Booking writes the ride, its
// history and the outbox event in one transaction and wakes the relay; matching then runs from Kafka.
import { check } from 'k6';
import {
  DURATION, MEASURED, SETUP_OPTIONS, VUS, createAccounts, loginAll, offset, place, post, randomPointWithin,
  reportThresholds, summaryFile,
} from './lib.js';

/** Pickups anywhere in the inner city; trips of a few kilometres. */
const PICKUP_RADIUS_METERS = 15000;
const TRIP_NORTH_METERS = 3000;
const CATEGORY = 'ECONOMY';

export const options = {
  ...SETUP_OPTIONS,
  scenarios: { [MEASURED]: { executor: 'constant-vus', vus: VUS, duration: DURATION } },
  thresholds: reportThresholds(['estimate', 'book', 'cancel']),
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return { passengers: loginAll(createAccounts(VUS, 'PASSENGER', 'booking')) };
}

export default function (data) {
  const passenger = data.passengers[(__VU - 1) % data.passengers.length];
  const pickup = randomPointWithin(PICKUP_RADIUS_METERS);
  const dropoff = offset(pickup, TRIP_NORTH_METERS, 0);

  const estimate = post('/api/fares/estimate', { pickup, dropoff }, passenger.token, 'estimate');
  if (!check(estimate, { 'estimate 200': (r) => r.status === 200 })) {
    return;
  }
  const quote = estimate.json('quotes').find((q) => q.vehicleCategory === CATEGORY);
  const booked = post('/api/rides', {
    quoteId: quote.quoteId, pickup: place(pickup, 'Load test pickup'), dropoff: place(dropoff, 'Load test dropoff'),
    paymentMethod: 'CASH',
  }, passenger.token, 'book');
  if (!check(booked, { 'book 201': (r) => r.status === 201 })) {
    return;
  }
  const cancelled = post(`/api/rides/${booked.json('id')}/cancel`, { reason: 'Load test' }, passenger.token, 'cancel');
  check(cancelled, { 'cancel 200': (r) => r.status === 200 });
}

export function handleSummary(summary) {
  return summaryFile('booking', summary);
}
