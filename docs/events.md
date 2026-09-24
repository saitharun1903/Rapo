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

Endpoint: `wss://<api-host>/ws` (native WebSocket, no SockJS). Spring's simple in-memory broker serves `/topic` and `/queue`, with the application prefix `/app` and the user prefix `/user`. Implemented in Phase 4; destinations are defined in `StompDestinations`.

Every push goes to a **per-user queue** (`/user/queue/...`), except the admin feed. Recipients are computed by the server when it sends, from current data: a ride update goes to the ride's passenger and its *current* driver. There are no per-ride topics a client could stay subscribed to after losing access (decision D20).

### 2.1 Connection

- The handshake needs no token (browsers cannot set headers on it), but the `Origin` must be one of `CORS_ALLOWED_ORIGINS`.
- The `CONNECT` frame carries `Authorization: Bearer <accessToken>` (the REST access token). `StompAuthenticationInterceptor` validates it. On failure the server sends an `ERROR` frame and closes the socket.
- A socket that has not sent a valid `CONNECT` within `rideflow.realtime.connect-timeout` (10 s) is closed.
- **Token lifetime:** once the access token expires, further `SUBSCRIBE`/`SEND` frames are refused. The server also closes the socket with close code **4001** (checked every 15 s). The client refreshes its token and reconnects (§2.5). Without this, a socket that only receives pushes would keep streaming after the token, or the account, stopped being valid (D21).
- Heartbeat `10000,10000`. Frames from one client may be processed concurrently. Location reports do not need ordering because a report older than the stored position is ignored.
- Metrics:
  - `rideflow_ws_sessions_active{role}`: open, authenticated sessions on this instance.
  - `rideflow_ws_location_dropped_total`: location messages dropped by the rate limit.
  - `rideflow_ws_push_failures_total`: pushes that could not be handed to the broker.
- Driver presence depends on location freshness, not socket state (§2.6).

### 2.2 Client → server

| Destination | Role | Payload | Rules |
|---|---|---|---|
| `/app/drivers/location` | DRIVER (online) | `{location: {lat, lng}, headingDeg?, speedMps?, accuracyMeters?, recordedAt}` (same body as `POST /api/drivers/location`) | At most one message per second per session; faster ones are dropped and counted. `recordedAt` must be at most 5 s in the future and at most 30 s old. A report older than the stored position is ignored |

A client may send nothing else. `SEND` to any other destination, including any `/topic` or `/queue` destination, is refused with `FORBIDDEN` and the socket is closed. This stops clients injecting messages into other users' streams.

A rejected location message (validation failure, `STALE_LOCATION`, `DRIVER_OFFLINE`) gets a reply on the sender's `/user/queue/errors`, and the socket **stays open**. One bad GPS fix must not interrupt the stream.

### 2.3 Server → client

| Destination | Recipient | Payload | Sent when |
|---|---|---|---|
| `/user/queue/ride-offers` | offered driver | `{type: "OFFER", rideId, offer: RideOfferResponse}` | a matching round offers the ride to this driver |
| | | `{type: "WITHDRAWN", rideId, offer: null}` | another driver accepted, or the passenger cancelled (the offer's own expiry is not pushed; `offer.expiresAt` is known) |
| `/user/queue/rides` | passenger and current driver | `RideResponse` (same as `GET /api/rides/{id}`, includes `version`) | every ride status change |
| `/user/queue/ride-location` | passenger of the driver's active ride | `{rideId, location: {lat, lng}, headingDeg, speedMps, recordedAt}` | each accepted location report while a driver is assigned (DRIVER_ASSIGNED to IN_PROGRESS) |
| `/user/queue/presence` | driver | `{availability: "OFFLINE", reason: "LOCATION_TIMEOUT"\|"ACCOUNT_SUSPENDED", occurredAt}` | the server took the driver offline |
| `/user/queue/errors` | sender | `{code, message, destination, fieldErrors}` | a location message was rejected |
| `/topic/admin/activity` | ADMIN | `{rideId, previousStatus, status, actor, rideVersion, occurredAt}` (no personal data) | every ride status change |

All payloads are JSON with the same conventions as the REST API: ISO-8601 instants, and money as a decimal string.

Pushes are sent after the database transaction commits, and are best effort. A lost push is repaired by the reconnect snapshot. Ride updates are sent asynchronously and may arrive out of order; clients apply one only if its `version` is newer than the one they show. Clients order location messages by `recordedAt`.

In Phase 6 the source of these pushes moves from in-process events to Kafka (§1.2, `realtime-{instance}` consumer group). The destinations and payloads stay the same. Phase 6 also adds `/user/queue/notifications` for the notifications consumer.

### 2.4 Subscription authorisation

`StompAuthorizationInterceptor` applies a deny-by-default allow-list:

- `/user/queue/{rides, ride-location, ride-offers, presence, errors}`: any authenticated user. Spring resolves these to the caller's own sessions, so they cannot address another user.
- `/topic/admin/activity`: ADMIN only.
- Anything else is refused with `FORBIDDEN` (`ERROR` frame, socket closed). This includes a session's resolved queue name (`/queue/rides-user<sessionId>`), the classic way to read another user's queue.

The `ERROR` frame's `message` header is the error code. Its JSON body is `{code, message, destination}`. Unexpected server errors return `INTERNAL_ERROR` with a generic message, never exception details.

### 2.5 Client reconnect protocol

1. On close or error, reconnect with exponential backoff and full jitter (1 s → 30 s cap). Refresh the access token first if it expires within 60 s, or if the close code was 4001.
2. After `CONNECTED`, re-subscribe to all destinations.
3. Fetch snapshots over REST: `GET /api/rides/active`, `GET /api/rides/{id}/tracking`, and for drivers `GET /api/drivers/me/offers`.
4. Apply a streamed ride update only if its `version` is newer than the locally known version.
5. The UI shows a non-blocking "Reconnecting…" banner. During an active ride it shows "Location signal lost" when no location message arrives for more than 15 s, or when the snapshot says `stale: true`.

### 2.6 Driver presence

- Matching already ignores positions older than `rideflow.matching.location-freshness` (30 s).
- `DriverPresenceSweeper` runs every 30 s. It sets AVAILABLE drivers with no location update for `rideflow.realtime.presence.timeout` (2 min) to OFFLINE, cancels their pending offers, and pushes `LOCATION_TIMEOUT` on `/user/queue/presence`.
- Each driver is re-checked under the driver row lock, so a report or an accept that raced the sweep wins.
- Drivers on a trip are never taken offline: lost GPS during a trip must not end the trip. The passenger's snapshot shows `stale: true` instead.
