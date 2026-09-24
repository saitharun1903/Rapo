# RideFlow — Events and Real-time Channels

## 1. Kafka

Kafka 4.x in KRaft mode. Dev: 3 partitions, replication factor 1. Prod: replication ≥ 3 (managed service). Topics are declared as `NewTopic` beans (auto-creation disabled on the broker).

### 1.1 Envelope

Every event is JSON with the same envelope. Payloads are Java records in `com.rideflow.kafka.event`.

```json
{
  "eventId": "0b8e5c1e-…",           // UUID, idempotency key (= outbox row id)
  "eventType": "ride.accepted",
  "schemaVersion": 1,                 // bump only for breaking changes; additive fields don't bump
  "occurredAt": "2026-09-24T10:15:30.123Z",
  "aggregateId": "5b0c…",             // rideId / driverId / paymentId
  "aggregateVersion": 4,              // rides.version after the change; consumers drop stale events
  "traceId": "4bf92f3577b34da6",
  "payload": { ... }
}
```

Headers: `eventType`, `schemaVersion`, `traceparent`.

### 1.2 Topics

| Topic | Key | Producer | Consumers (group) | Retention |
|---|---|---|---|---|
| `ride.requested` | rideId | RideService (outbox) | `matching`, `realtime-{instance}` | 7 d |
| `ride.driver.assigned` | rideId | DriverMatchingService (outbox) | `notifications`, `realtime-{instance}` | 7 d |
| `ride.accepted` | rideId | RideService (outbox) | `notifications`, `realtime-{instance}` | 7 d |
| `ride.driver.arriving` | rideId | RideService (outbox) | `notifications`, `realtime-{instance}` | 7 d |
| `ride.driver.arrived` | rideId | RideService (outbox) | `notifications`, `realtime-{instance}` | 7 d |
| `ride.started` | rideId | RideService (outbox) | `notifications`, `realtime-{instance}` | 7 d |
| `ride.completed` | rideId | RideService (outbox) | `payments`, `trip-analysis`, `notifications`, `realtime-{instance}` | 7 d |
| `ride.cancelled` | rideId | RideService (outbox) | `matching` (release offers/locks), `notifications`, `realtime-{instance}` | 7 d |
| `ride.expired` | rideId | MatchingSweeper (outbox) | `notifications`, `realtime-{instance}` | 7 d |
| `driver.location.updated` | driverId | LocationIngestionService (direct) | `location-persistence` (batch), `realtime-{instance}` | 6 h |
| `payment.created` | rideId | PaymentService (outbox) | `notifications`, `realtime-{instance}` | 7 d |
| `notification.requested` | userId | admin/driver verification services (outbox) | `notifications` | 3 d |
| `<topic>.DLT` | same | DeadLetterPublishingRecoverer | none (inspected via admin/system + tooling) | 14 d |

`realtime-{instance}` is a per-instance consumer group (`auto.offset.reset=latest`) so every backend instance can push to the WebSocket clients connected to it.

### 1.3 Payloads (schemaVersion 1)

```jsonc
// ride.requested
{ "rideId", "passengerId", "vehicleCategory", "pickup": {"lat","lng"}, "dropoff": {"lat","lng"}, "requestedAt" }

// ride.driver.assigned   (offers created for a matching round)
{ "rideId", "round", "offers": [ { "offerId", "driverId", "distanceMeters", "expiresAt" } ],
  "pickup": {"lat","lng","address"}, "dropoff": {"lat","lng","address"},
  "estimatedFare": {"amount","currency"}, "estimatedDistanceMeters" }

// ride.accepted
{ "rideId", "passengerId", "driverId", "vehicleId", "acceptedAt", "withdrawnOfferDriverIds": [] }

// ride.driver.arriving / ride.driver.arrived / ride.started
{ "rideId", "passengerId", "driverId", "at" }

// ride.completed
{ "rideId", "passengerId", "driverId", "completedAt", "actualDistanceMeters", "actualDurationSeconds",
  "distanceSource", "finalFare": {"amount","currency"}, "paymentMethod" }

// ride.cancelled
{ "rideId", "passengerId", "driverId?", "cancelledBy", "reason", "previousStatus" }

// ride.expired
{ "rideId", "passengerId", "rounds", "finalRadiusMeters" }

// driver.location.updated
{ "driverId", "activeRideId?", "rideStatus?", "lat", "lng", "headingDeg?", "speedMps?", "accuracyMeters?", "recordedAt" }

// payment.created
{ "paymentId", "rideId", "passengerId", "driverId", "amount", "currency", "method", "status", "driverEarnings" }

// notification.requested
{ "userId", "type", "title", "body", "rideId?" }
```

Payloads contain IDs and ride data needed by consumers. They never contain emails, phone numbers, passwords or tokens.

### 1.4 Delivery semantics

- **Producer:** outbox + relay (`acks=all`, idempotent producer). Location events are produced directly (at-most-once is acceptable for superseded pings).
- **Consumer:** at-least-once. Each handler is idempotent: a state check (`ride.status` must be the expected source state) and/or an insert into `processed_events` in the same DB transaction as the side effect.
- **Retries:** 3 attempts with exponential backoff (1 s, 2 s, 4 s), then `.DLT`. Non-retryable exceptions (deserialisation, validation, `InvalidRideTransitionException` for stale events) go straight to the DLT or are logged and skipped, according to a classification table in `KafkaErrorHandlingConfig`.

---

## 2. WebSocket (STOMP)

Endpoint: `wss://<api-host>/ws` (native WebSocket, no SockJS). Simple in-memory broker for `/topic` and `/queue`, with the application prefix `/app` and user prefix `/user`.

### 2.1 Connection

- `CONNECT` frame header `Authorization: Bearer <accessToken>`, validated by `StompAuthChannelInterceptor`. On failure, an `ERROR` frame is sent and the connection closes.
- Heartbeat `10000,10000`.
- The server records `rideflow_ws_sessions_active{role}`. On disconnect it only decrements the metric. Driver presence is governed by location freshness, not socket state.

### 2.2 Client → server

| Destination | Role | Payload | Notes |
|---|---|---|---|
| `/app/drivers/location` | DRIVER (online) | `{lat, lng, headingDeg?, speedMps?, accuracyMeters?, recordedAt}` | ≥ 1 s between messages per session (excess dropped and counted). `recordedAt` must be ≤ 5 s in the future and ≤ 30 s old. Accuracy > 100 m is ignored |

### 2.3 Server → client

| Destination | Subscriber | Payload | Source |
|---|---|---|---|
| `/user/queue/ride-offers` | DRIVER | `{type: OFFER\|WITHDRAWN, offerId, rideId, pickup, dropoff, distanceToPickupMeters, estimatedFare, expiresAt}` | `ride.driver.assigned`, `ride.accepted`, `ride.cancelled` |
| `/topic/rides/{rideId}/status` | ride participants | `{rideId, status, aggregateVersion, occurredAt, driver?, finalFare?}` | all `ride.*` topics |
| `/topic/rides/{rideId}/location` | ride passenger | `{rideId, lat, lng, headingDeg, recordedAt, etaSeconds?}` | `driver.location.updated` with `activeRideId` |
| `/user/queue/notifications` | any | `NotificationResponse` | notifications consumer |
| `/user/queue/errors` | any | `{code, message, destination}` | validation / authorisation failures |
| `/topic/admin/activity` | ADMIN | `{eventType, rideId, status, occurredAt}` | all `ride.*` topics |

### 2.4 Subscription authorisation

`SUBSCRIBE` frames are checked by `StompSubscriptionInterceptor`:

- `/topic/rides/{rideId}/status`: passenger or assigned driver of the ride (`ride:{id}:participants` in Redis, rebuilt from the DB on a cache miss).
- `/topic/rides/{rideId}/location`: the passenger of the ride (and the assigned driver, for their own map).
- `/topic/admin/**`: ADMIN only.
- `/user/**` is resolved to the authenticated principal by Spring, so users cannot address others.

### 2.5 Client reconnect protocol

1. On close or error, reconnect with exponential backoff and full jitter (1 s → 30 s cap). Refresh the access token first if it expires within 60 s.
2. After `CONNECTED`, re-subscribe to all destinations.
3. Fetch snapshots over REST: `GET /rides/active`, `GET /rides/{id}/tracking`, and for drivers `GET /drivers/me/offers`.
4. Apply streamed status messages only if `aggregateVersion` > the locally known version.
5. The UI shows a non-blocking "Reconnecting…" banner, and "Location signal lost" when no location message arrives for more than 15 s during an active ride.
