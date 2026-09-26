# RideFlow — Events and Real-time Channels

## 1. Kafka

Kafka 4.x in KRaft mode. Implemented in Phase 6 (`com.rideflow.kafka`). Every topic and its dead-letter topic is declared by the application (`KafkaConfig`, `NewTopic` beans), and the broker does not create topics implicitly. Development uses 3 partitions and replication factor 1; production uses replication ≥ 3 (`KAFKA_REPLICATION_FACTOR`).

Deployed names are `rideflow.kafka.prefix` + the base name below. The prefix is empty by default. It lets several environments share one cluster, and the integration tests give every Spring context a random prefix so contexts never consume each other's events.

### 1.1 Envelope

Every event is JSON with the same envelope (`EventEnvelope`, written and read by `EventCodec`). The payload is the domain event record itself (`com.rideflow.service.*.event`), so producer and consumer share one definition.

```jsonc
{
  "eventId": "0b8e5c1e-…",           // UUID, idempotency key (= outbox row id)
  "eventType": "ride.accepted",       // = topic base name
  "schemaVersion": 1,                 // changes only for breaking payload changes
  "occurredAt": "2026-09-25T10:15:30.123456Z",
  "aggregateId": "5b0c…",             // rideId / driverId / paymentId / userId; also the Kafka key
  "aggregateVersion": 4,              // rides.version after the change; null for other aggregates
  "traceId": "4bf92f3577b34da6",      // X-Request-Id of the producing request, for log correlation
  "payload": { }
}
```

- **Headers:** `eventType` and `schemaVersion`, so tools can filter without parsing JSON.
- **Evolution:** readers ignore unknown fields. A producer can therefore add fields without a version change while older consumers still run. Removing or changing a field is a breaking change and needs a new `schemaVersion`.
- **Unknown versions:** a consumer that receives a `schemaVersion` it does not read sends the record to the DLT (§1.4).

### 1.2 Topics

| Topic | Payload | Key | Delivery | Consumer groups | Retention |
|---|---|---|---|---|---|
| `ride.requested` | `RideStatusChangedEvent` (→ REQUESTED) | rideId | outbox | `matching`, `realtime-{instance}` | 7 d |
| `ride.matching` | `RideStatusChangedEvent` (→ MATCHING: first round, or re-dispatch) | rideId | outbox | `notifications`, `realtime-{instance}` | 7 d |
| `ride.accepted` | `RideStatusChangedEvent` (→ DRIVER_ASSIGNED) | rideId | outbox | `notifications`, `realtime-{instance}` | 7 d |
| `ride.driver.arriving` | `RideStatusChangedEvent` | rideId | outbox | `notifications`, `realtime-{instance}` | 7 d |
| `ride.driver.arrived` | `RideStatusChangedEvent` | rideId | outbox | `notifications`, `realtime-{instance}` | 7 d |
| `ride.started` | `RideStatusChangedEvent` (→ IN_PROGRESS) | rideId | outbox | `notifications`, `realtime-{instance}` | 7 d |
| `ride.completed` | `RideStatusChangedEvent` | rideId | outbox | `payments`, `notifications`, `trip-analysis`, `realtime-{instance}` | 7 d |
| `ride.cancelled` | `RideStatusChangedEvent` | rideId | outbox | `notifications`, `realtime-{instance}` | 7 d |
| `ride.expired` | `RideStatusChangedEvent` | rideId | outbox | `notifications`, `realtime-{instance}` | 7 d |
| `ride.dispatch.requested` | `MatchingRoundRequestedEvent` | rideId | outbox | `matching` | 7 d |
| `ride.driver.assigned` | `RideOffersCreatedEvent` (offers sent in a matching round) | rideId | outbox | `realtime-{instance}` | 7 d |
| `ride.offers.withdrawn` | `RideOffersWithdrawnEvent` | rideId | outbox | `realtime-{instance}` | 7 d |
| `driver.location.updated` | `DriverLocationUpdatedEvent` | driverId | **direct** | `location-persistence` (batch), `realtime-{instance}` | 6 h |
| `driver.offline` | `DriverWentOfflineEvent` | driverId | outbox | `realtime-{instance}` | 7 d |
| `payment.created` | `PaymentCreatedEvent` | paymentId | outbox | `notifications` | 7 d |
| `notification.requested` | `NotificationRequestedEvent` (driver verified or rejected) | userId | outbox | `notifications` | 3 d |
| `notification.created` | `NotificationCreatedEvent` | userId | outbox | `realtime-{instance}` | 3 d |
| `ride.message.sent` | `RideMessageSentEvent` `{messageId, rideId, passengerId, driverId}`: ids only, the bridge loads the text so message bodies never sit in Kafka | rideId | outbox | `realtime-{instance}` | 3 d |
| `<topic>.DLT` | the failed record, unchanged, plus Spring's `kafka_dlt-*` headers (original topic, partition, offset, exception class and message) | same | — | none (inspect and replay by hand) | 14 d |

Why these topics:

- **One topic per target ride status**, instead of one `ride.status-changed` topic. Consumers subscribe to the transitions they act on (payments reads only `ride.completed`) and do not filter every change. The names keep the domain vocabulary. The cost is that a ride's events are ordered within a topic but not across topics, so consumers must not rely on cross-topic order (§1.4).
- **`ride.dispatch.requested`** asks matching for its next round early: the last open offer was rejected, or the driver backed out. New rides are matched from `ride.requested`. Round-to-round progress on timeouts stays with `MatchingSweeper`, which reads the database. It also covers rides whose trigger is late, for example while Kafka is down.
- **`notification.created`** exists because the notifications consumer runs on one instance, but the user may be connected to another. Storing the notification and pushing it are therefore separate steps.
- **`realtime-{instance}`** is a consumer group per running instance (random id at startup), so every instance receives every event and can push it to the WebSocket clients connected to it.

### 1.3 Payloads (schemaVersion 1)

Fields of the payload records (`?` = may be null):

```jsonc
// ride.* status topics: RideStatusChangedEvent
{ "rideId", "from?", "to", "rideVersion", "passengerId", "driverId?", "releasedDriverId?",
  "actor": "PASSENGER|DRIVER|SYSTEM|ADMIN", "reason?", "occurredAt" }
// releasedDriverId: the driver detached by a re-dispatch (→ MATCHING)

// ride.dispatch.requested: MatchingRoundRequestedEvent
{ "rideId" }

// ride.driver.assigned: RideOffersCreatedEvent
{ "rideId", "round", "driverIds": [], "expiresAt" }

// ride.offers.withdrawn: RideOffersWithdrawnEvent
{ "rideId", "driverIds": [] }

// driver.location.updated: DriverLocationUpdatedEvent
{ "driverId", "rideId?", "passengerId?", "rideStatus?", "location": {"lat", "lng"}, "headingDeg?", "speedMps?",
  "accuracyMeters?", "recordedAt", "receivedAt", "destination?": {"target": "PICKUP|DROPOFF", "point": {"lat", "lng"}} }

// driver.offline: DriverWentOfflineEvent
{ "driverId", "reason": "LOCATION_TIMEOUT|ACCOUNT_SUSPENDED", "occurredAt" }

// payment.created: PaymentCreatedEvent
{ "paymentId", "rideId", "passengerId", "driverId", "amount", "currency", "method", "status", "driverEarnings", "occurredAt" }

// notification.requested: NotificationRequestedEvent
{ "userId", "type", "rideId?", "detail?" }

// notification.created: NotificationCreatedEvent
{ "notificationId", "userId", "type", "title", "body", "rideId?", "createdAt" }
```

Payloads carry IDs and ride data, not full views. A consumer that needs more reads the database, which is the source of truth: the payments consumer, for example, loads the final fare. Payloads never contain emails, phone numbers, names, passwords or tokens. A driver's position is in `driver.location.updated` together with the one passenger allowed to see it.

### 1.4 Delivery semantics

**Producing**
- **Outbox** (everything except positions). `OutboxDomainEventPublisher` writes the event to `outbox_events` in the caller's transaction and refuses to run outside one. `OutboxRelay` sends rows in insertion order through an idempotent producer (`acks=all`). It marks a row published only after the broker acknowledges it.
  - The relay runs when a transaction that wrote events commits, and otherwise every 250 ms. Every instance runs one, and `FOR UPDATE SKIP LOCKED` keeps them on separate rows.
  - A row that fails or times out (10 s) keeps its `attempts` and `last_error` and is sent again, so delivery is **at least once**.
- **Direct** (`driver.location.updated`). Sent straight to Kafka, fire-and-forget, **at most once**. The next report supersedes a lost one within seconds. Failures are counted (`rideflow_location_publish_failures_total`); while they persist, only the first failure and the recovery are logged.

**Consuming**
- Consumers are **at least once**: offsets are committed after the handler returns.
- Handlers with side effects insert `(consumer, eventId)` into `processed_events` in the same transaction as the side effect (`ProcessedEvents`). A redelivered event is skipped; a rolled-back one is processed again. This covers matching, payments, notifications and trip analysis.
- `trip-analysis` fetches one record per poll and allows 15 minutes between polls, because a local model can take minutes per trip. AI failures are recorded on the analysis and never thrown, so they are neither retried by Kafka nor dead-lettered.
- Natural idempotency adds a second guard:
  - one payment per ride (`payments.ride_id` unique);
  - one notification per user and event (`notifications (user_id, source_event_id)` unique);
  - positions never move backwards (upsert only if newer);
  - a repeated track point is within the sampling distance of itself.
- **Ordering:** per key within a topic. Across topics nothing is guaranteed.
  - Matching re-checks the ride under its row lock.
  - Payments require status COMPLETED.
  - WebSocket clients apply a ride update only if its `version` is newer.
  - A late notification is still a true statement about the past.

**Failures** (`KafkaConfig`)
- Shared groups retry a failed record 3 times with exponential backoff (1 s, 2 s, 4 s). They then publish it to `<topic>.DLT` (same partition) and move on.
- Records that can never succeed (`EventDecodingException`: not JSON, unknown schema version, wrong type for the topic, no aggregate id) go to the DLT immediately.
- Each dead-lettered record increments `rideflow_kafka_dead_letters_total{topic}` and is logged with topic, partition and offset.
- The location batch consumer persists the records before a bad one, dead-letters that one and continues after it.
- The realtime bridge neither retries nor dead-letters. Every instance would write the same record to the DLT, and pushes are best effort. It logs and moves on.

**Kafka unavailable**
- Requests keep working, and events wait in the outbox (`rideflow_outbox_pending`).
- Matching falls back to `MatchingSweeper`, which starts a ride's first round once `rideflow.matching.trigger-grace` (5 s) has passed without a trigger.
- Live pushes and notifications stop.
- Positions still reach Redis, so the pickup geofence, tracking snapshots and presence checks still work, but they stop reaching PostgreSQL. Matching then sees drivers as stale after `location-freshness` (30 s).
- Everything resumes when the broker returns, except the positions reported during the outage. Those are dropped, which is acceptable because they have since been superseded.

**Cleanup:** `EventHousekeepingJob` (03:30 daily) purges published outbox rows after 3 days and `processed_events` rows after 7 days. 7 days is longer than any redelivery window, because Kafka keeps ride topics for 7 days.

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
| `/app/drivers/location` | DRIVER (online) | `{location: {lat, lng}, headingDeg?, speedMps?, accuracyMeters?, recordedAt}` (same body as `POST /api/drivers/location`) | At most one message per second per session; faster ones are dropped and counted. `recordedAt` must be at most 5 s in the future and at most 30 s old. A report older than the stored position is ignored. Accepted reports go to Redis and `driver.location.updated` (architecture §8) |

A client may send nothing else. `SEND` to any other destination, including any `/topic` or `/queue` destination, is refused with `FORBIDDEN` and the socket is closed. This stops clients injecting messages into other users' streams.

A rejected location message (validation failure, `STALE_LOCATION`, `DRIVER_OFFLINE`) gets a reply on the sender's `/user/queue/errors`, and the socket **stays open**. One bad GPS fix must not interrupt the stream.

### 2.3 Server → client

| Destination | Recipient | Payload | Sent when |
|---|---|---|---|
| `/user/queue/ride-offers` | offered driver | `{type: "OFFER", rideId, offer: RideOfferResponse}` | a matching round offers the ride to this driver |
| | | `{type: "WITHDRAWN", rideId, offer: null}` | another driver accepted, or the passenger cancelled (the offer's own expiry is not pushed; `offer.expiresAt` is known) |
| `/user/queue/rides` | passenger and current driver | `RideResponse` (same as `GET /api/rides/{id}`, includes `version`) | every ride status change |
| `/user/queue/ride-location` | passenger of the driver's active ride | `{rideId, location: {lat, lng}, headingDeg, speedMps, recordedAt, eta: {target, seconds, distanceMeters, source, computedAt}?}`. The ETA is the Redis-cached value (refreshed in the background at most every 30 s); it is `null` until the first one is computed and while the driver waits at the pickup | each accepted location report while a driver is assigned (DRIVER_ASSIGNED to IN_PROGRESS) |
| `/user/queue/presence` | driver | `{availability: "OFFLINE", reason: "LOCATION_TIMEOUT"\|"ACCOUNT_SUSPENDED", occurredAt}` | the server took the driver offline |
| `/user/queue/notifications` | the notified user | `{id, type, title, body, rideId?, read: false, createdAt}` (same as the items of `GET /api/notifications`) | the notifications consumer stored a notification (ride progress, payment, verification decision) |
| `/user/queue/ride-messages` | passenger and assigned driver | `{id, rideId, senderId, senderRole, body, sentAt}` (same as `GET /api/rides/{id}/messages`) | a chat message was stored (both sides get it, so the sender's other devices see it too) |
| `/user/queue/errors` | sender | `{code, message, destination, fieldErrors}` | a location message was rejected |
| `/topic/admin/activity` | ADMIN | `{rideId, previousStatus, status, actor, rideVersion, occurredAt}` (no personal data) | every ride status change |

All payloads are JSON with the same conventions as the REST API: ISO-8601 instants, and money as a decimal string.

Pushes come from Kafka: every instance's realtime bridge (`realtime-{instance}` group, §1.2) consumes the events and pushes to the clients connected to that instance. It checks that a recipient is connected there before it loads anything from the database. Events only exist once their transaction has committed, so a push never announces a change that rolled back.

Pushes are best effort, and a lost push is repaired by the reconnect snapshot. Ride updates may arrive out of order, so clients apply one only if its `version` is newer than the one they show. Clients order location messages by `recordedAt`.

### 2.4 Subscription authorisation

`StompAuthorizationInterceptor` applies a deny-by-default allow-list:

- `/user/queue/{rides, ride-location, ride-offers, presence, notifications, errors, ride-messages}`: any authenticated user. Spring resolves these to the caller's own sessions, so they cannot address another user.
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
