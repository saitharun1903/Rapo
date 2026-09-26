# Raido feature specification

This document covers, for every feature Raido builds:
- the behaviour, the data model and the API;
- the events, security and privacy rules;
- the tests and the known limits.

Priorities and phases are in [product-roadmap.md](product-roadmap.md). Features that are only designed, not built,
are marked **Deferred**.

## Conventions

These apply to every feature below:

- **Migrations.** Flyway, numbered from V8. Constraints live in the database, not only in code. Every foreign key
  that is queried gets an index.
- **Endpoints.**
  - Each has a DTO, a controller, a service and a repository.
  - Requests are validated with Bean Validation.
  - Errors use the existing `ErrorResponse` codes through `GlobalExceptionHandler`.
  - Roles are declared in `SecurityConfig`. Ownership is checked in the service, where a stranger gets a 404,
    never a 403, so ids cannot be probed.
- **Events.**
  - A Kafka topic is added only where the change must reach users connected to other instances (through the
    per-instance realtime bridge) or must be processed asynchronously.
  - New topics go through the outbox.
- **Logs.** Never passwords, tokens, PINs, share tokens, message bodies or exact coordinates. Ids and coarse
  values only.
- **Metrics.** Prefixed `rideflow.` to match the existing dashboards; the product name is not part of metric
  names.

---

## 1. State-driven ride experience (P0)

Same data as today (`RideResponse`, tracking, pushes), new presentation. Each state has one headline, one primary
action and one map mode.

| State | Headline | Panel content | Map mode |
|---|---|---|---|
| REQUESTED | Request received | Route summary, fare, cancel | Pickup and drop-off, route |
| MATCHING | Finding your ride | Matching round and radius from the ride (`matchingRound`, `matchingRadiusMeters`), nearby-car count (fuzzed), cancel | Pulsing search ring of the current radius around the pickup |
| DRIVER_ASSIGNED | Driver on the way | Driver card (monogram, verified badge, rating, vehicle, plate), ETA, PIN, chat, safety, cancel | Driver, route from driver to pickup, fit once |
| DRIVER_ARRIVING | Arriving in *n* min | As above, ETA prominent | Follow the driver until the user pans |
| DRIVER_ARRIVED | Your driver is here | Plate large, PIN large, chat, safety, cancel | Driver and pickup, close zoom |
| IN_PROGRESS | On the way to *destination* | ETA to destination, route progress, share trip, safety | Travelled and remaining route, follow |
| COMPLETED | You've arrived | Final fare with the change from the estimate explained, rating, Trip Intelligence, ride again | Full trip |
| CANCELLED | Ride cancelled | Who cancelled and why, book again | Pickup and drop-off |
| EXPIRED | No driver accepted in time | Retry with the same places | Pickup and drop-off |

**Movement.**
- The panel changes state with a 200 ms cross-fade.
- The driver card rises when a driver is assigned.
- The completion summary expands.
- Nothing else animates.
- With `prefers-reduced-motion`, all movement becomes instant, map camera moves included.

---

## 2. Map (P0)

- **Provider interface.** `lib/map/theme.ts` exposes `MapTheme { styleUrl(theme), transformStyle(style, theme) }`.
  The default provider is OpenFreeMap: *positron* for light and *dark* for dark. `NEXT_PUBLIC_MAP_STYLE_URL` still
  overrides both.
  - `transformStyle` recolours water, parks, buildings and roads to the design tokens and demotes minor labels.
  - It works on any vector style with the same OpenMapTiles layer names, and leaves other styles untouched.
- **Driver marker.**
  - Positioned by a `requestAnimationFrame` loop that interpolates from the previous to the new fix over the
    interval between their timestamps, capped at 3 s.
  - The loop writes the marker transform directly, so neither React nor the map re-renders per frame.
  - Heading rotates along the shortest arc.
  - A fix older than 15 s greys the marker.
- **Camera.**
  - Fits once when the phase changes: booking, approach, trip or done.
  - Follows the driver during approach and trip until the user drags or zooms.
  - A *recenter* control resumes following.
- **Controls.** Zoom, locate me (browser geolocation with an accuracy circle), recenter.
- **Routes.**
  - During approach, driver to pickup, re-queried when the driver has moved 150 m or 30 s have passed.
  - During the trip, pickup to destination, split into travelled and remaining at the driver's projection onto
    the line.
- **States.** A skeleton while loading. An error card with *Retry* when the style or tiles fail; the ride panel
  keeps working.

---

## 3. In-ride chat (P1)

**Behaviour.**
- Participants can exchange short messages from DRIVER_ASSIGNED to IN_PROGRESS. Quick replies are "I'm at the
  pickup", "On my way" and "Running a few minutes late".
- Chat closes when the ride ends. Messages are deleted 30 days after the ride ends.

**Data.** `ride_messages`
- Columns: `id uuid pk`, `ride_id fk`, `sender_id fk users`, `sender_role`, `body varchar(500)`, `sent_at`.
- Index `(ride_id, sent_at)`.
- CHECK: `body` is not blank.

**API.**
- `GET /api/rides/{id}/messages`
- `POST /api/rides/{id}/messages {body}`, which returns 201. Only in the states above; otherwise
  `409 CHAT_CLOSED`. Rate limit `CHAT` is 20 per minute per user.

**Events.** Outbox topic `ride.message.sent` (ids only; the bridge loads the body) goes to the realtime bridge,
which pushes to `/user/queue/ride-messages` for both participants.

**Security.** Participants only; strangers get a 404. The stored body is never logged.

---

## 4. Safety Center (P1)

A shield button on every active ride opens a sheet with the following actions.

| Action | Rider | Driver |
|---|---|---|
| SOS | ✓ | ✓ |
| Share trip | ✓ | ✓ (their own trip) |
| Boarding PIN | shows PIN | enters PIN |
| Verified driver / vehicle | ✓ | — |
| Report an issue | ✓ | ✓ |
| Emergency information | 112 and the configured local numbers as `tel:` links | same |

### 4.1 Safety events

**Data.** `safety_events`
- **Identity:** `id uuid pk`, `ride_id fk`.
- **Classification:**
  - `type`: SOS, LONG_STOP, ROUTE_DEVIATION, SIGNAL_LOST, LONG_TRIP, ARRIVAL_CHECK or PIN_LOCKED.
  - `source`: USER or MONITOR.
  - `raised_by fk users null`.
  - `severity`: INFO, WARNING or CRITICAL.
- **Status:** OPEN, CONFIRMED_OK, HELP_REQUESTED, RESOLVED or DISMISSED.
- **Measurements:** `latitude`, `longitude` (visible to admins only), `details jsonb` holding the measured value
  and the threshold.
- **Lifecycle:** `created_at`, `responded_at`, `resolved_by`, `resolved_at`, `resolution_note`.
- **Constraints and indexes:**
  - Partial unique index: one OPEN event per `(ride_id, type)`.
  - Index `(status, created_at)`.

**Rules.**
- Events are append-only apart from the status fields. Every status change is written to `audit_logs`.
- Outbox topic `safety.event.detected`:
  - the realtime bridge pushes it to `/user/queue/safety` for the ride's passenger (and the driver for SOS);
  - it also goes to `/topic/admin/safety`;
  - the notification consumer creates an in-app notification.

### 4.2 SOS

`POST /api/rides/{id}/sos {location?}`:
- Open to the passenger or driver from DRIVER_ASSIGNED until 30 minutes after the ride ends.
- Creates a CRITICAL SOS event, or returns the open one: the call is idempotent and never rate limited.
- Returns the emergency numbers.

The UI says plainly that Raido's operations team is alerted and that the rider should call 112 in an emergency.
**Raido does not contact emergency services itself.**

### 4.3 Share my trip

**Data.** `trip_shares`
- Columns: `id`, `ride_id fk`, `created_by fk`, `token_hash char(64) unique` (SHA-256 of 32 random bytes; the token
  itself exists only in the URL), `created_at`, `expires_at`, `revoked_at`.
- A participant can hold at most 5 active shares per ride.

**API.**
- `POST /api/rides/{id}/shares` returns `{url, expiresAt}`.
- `DELETE /api/rides/{id}/shares/{shareId}` revokes a share.
- `GET /api/share/{token}` is public and rate limited (`SHARE_VIEW`, 60 per minute per IP).

**Public view.** Status, the driver's first name, vehicle and plate, the pickup and drop-off labels as entered,
the ETA, and the driver's position **rounded to 3 decimals (about 110 m)** while a driver is engaged.
- No passenger name, no ids and no exact coordinates.
- When the ride ends, the view shows only the final status. The link stops working 2 hours after the end.
- The page polls every 10 s; it needs no WebSocket and no account.

### 4.4 Trusted contacts

**Data.** `trusted_contacts`
- Columns: `id`, `user_id fk`, `name varchar(80)`, `phone varchar(20)` (E.164), `share_policy` (MANUAL,
  EVERY_RIDE or NIGHT_RIDES), `created_at`.
- At most 5 per user (checked in the service and by a trigger-free count query under the user's row lock).

**API.** `GET`, `POST`, `PUT` and `DELETE` on `/api/users/me/trusted-contacts`.

**Behaviour.**
- When a ride is assigned and a contact's policy matches (night means 22:00 to 06:00 in the service time zone,
  configurable), Raido creates a trip share and tells the rider in the app that a link is ready for that contact.
- The rider sends it with the device share sheet.
- Automatic SMS delivery is behind a `ContactNotifier` interface whose only implementation is disabled, because
  Raido has no SMS provider; the UI never claims a message was sent.

### 4.5 Boarding PIN

**Data.** `ride_pins`
- Columns: `ride_id pk fk`, `pin char(4)`, `attempts smallint`, `verified_at`, `created_at`.
- CHECK: `pin ~ '^[0-9]{4}$'`.

**Configuration.** `rideflow.safety.boarding-pin.enabled` (default true). A rider preference, *Require a PIN*
(`rider_safety_preferences.require_pin`), defaults to `rideflow.safety.boarding-pin.default-for-riders`:
- true in production;
- false in the `demo` profile, where simulated drivers cannot hear a PIN spoken in the car. The e2e test turns it
  on and drives both sides.

**Behaviour.**
- A PIN is generated with `SecureRandom` when a driver accepts. Only the passenger sees it
  (`RideResponse.boardingPin`, passenger view only).
- `POST /api/rides/{id}/start` takes `{pin}`. The server rejects `DRIVER_ARRIVED → IN_PROGRESS` without a correct
  PIN: `422 PIN_INCORRECT` with the attempts left.
- After 5 failures, `423 PIN_LOCKED` records a PIN_LOCKED safety event. The passenger can issue a new PIN
  (`POST /api/rides/{id}/pin`), which resets the attempts.
- Comparison is constant-time. PINs are never logged.

### 4.6 Ride safety monitor

A scheduled sweeper (`SafetyMonitor`, every 30 s, safe on every instance because the partial unique index
de-duplicates) runs one query over IN_PROGRESS rides:
- joined to `driver_locations`, the latest persisted fix, which the batched Kafka consumer writes;
- joined to `ride_routes`, the planned route stored at booking;
- joined to the last `ride_track_points` row.

**It adds no database work per location ping.**

| Signal | Rule (defaults, all configurable under `rideflow.safety.monitor`) | Severity |
|---|---|---|
| LONG_STOP | The driver is still reporting (fix under 60 s old) but has not moved 25 m for 4 min (the newest track point, or the start, is older than 4 min), and is more than 300 m from the drop-off | WARNING |
| ROUTE_DEVIATION | The fix is more than 500 m from the planned route, only when the route was ROUTED (not a straight-line fallback) | WARNING |
| SIGNAL_LOST | The driver's last fix is more than 120 s old | WARNING |
| LONG_TRIP | Time since start is more than max(2 × estimated duration, estimate + 20 min) | INFO |

**Rider check-in.**
- The rider sees "We noticed an unusual stop. Are you okay?", with the same neutral wording adapted per signal.
- The choices are *I'm okay*, *Get help*, *Share trip* and *Contact support*.
- `POST /api/rides/{id}/safety-events/{eventId}/response {answer: OK | HELP}`:
  - *OK* sets CONFIRMED_OK.
  - *HELP* sets HELP_REQUESTED and escalates to a CRITICAL admin alert.
- An unanswered event stays OPEN and rises in the admin queue. **Nothing is escalated outside Raido
  automatically.**

**Planned route.** `ride_routes`
- Columns: `ride_id pk`, `geometry geography(LineString,4326)`, `source`.
- Written at booking from the routing service, whose cache is warm from the estimate.

**Known limits.**
- A driver legitimately stuck in traffic produces LONG_STOP. That is why the wording is a question, not an
  accusation.
- Off-route detection is off for straight-line estimates.

### 4.7 Arrival check-in (P2)

- If the rider's preference *Check in after night rides* is on (default on), completing a night ride creates an
  ARRIVAL_CHECK event, shown in the app as "Did you get there safely?".
- The rider answers *I'm okay* or *Report an issue*. An unanswered check is only recorded.

### 4.8 Privacy fix (P0)

`GET /api/rides/{id}` must not return the passenger's name to a driver who holds only a pending offer. The name is
included for the assigned driver only. This is covered by an integration test.

### 4.9 Recording (P3, deferred)

- Design: an `EvidenceRecorder` interface with a disabled implementation.
- A future implementation would require:
  - explicit opt-in per ride;
  - an in-browser `MediaRecorder` with encryption on the device;
  - upload only when attached to a support ticket;
  - a retention period per region.
- Not built: browsers stop recording when the tab is backgrounded on mobile, which a safety feature cannot
  tolerate.

---

## 5. Saved places (P1)

**Data.** `saved_places`
- Columns: `id`, `user_id fk`, `kind` (HOME, WORK or OTHER), `label varchar(40)`, `address varchar(255)`,
  `latitude`, `longitude`, `created_at`, `updated_at`.
- Partial unique indexes: one HOME and one WORK per user.
- At most 20 places per user.
- Coordinates must be inside the service area, using the same `ServiceAreaPolicy` as booking.

**API.** `GET`, `POST`, `PATCH` and `DELETE` on `/api/users/me/places`.

**UI.** The booking field offers saved places first. Places are managed in Settings.

## 6. Ride again (P1)

- Frontend only. From trip history or trip details, *Ride again* opens booking with the same pickup, drop-off,
  category and payment method, then **requests a fresh estimate**.
- Old prices are never shown as current.
- If the old category is unavailable, the default is chosen and the user is told.

## 7. Scheduled rides (P1)

**Data.** `scheduled_rides`
- **Trip:**
  - `id`, `passenger_id fk`.
  - Pickup and drop-off coordinates and labels.
  - `category`, `payment_method`, `pickup_at`.
- **Price:**
  - `quoted_total`, `quoted_currency`, `pricing_version` (the price locked at scheduling, without surge).
- **Status:** SCHEDULED, DISPATCHING, DISPATCHED, CANCELLED or FAILED.
- **Outcome:**
  - `ride_id fk null`, `failure_reason`.
  - `reminder_sent_at`, `created_at`, `cancelled_at`.
- **Constraints:**
  - `ride_id` is required when the status is DISPATCHED.
  - `pickup_at > created_at`.
- **Index:** `(status, pickup_at)`.

**Rules.**
- `pickup_at` must be between 30 minutes and 7 days ahead.
- At most 5 upcoming per passenger, at least 30 minutes apart.

**Dispatch.** `ScheduledRideDispatcher` runs every 30 s.
1. It claims due rows (`pickup_at − lead ≤ now`, lead 10 min) with `FOR UPDATE SKIP LOCKED`.
2. It books the ride through the same booking service as an interactive request, with the locked price (surge
   1.0), which emits `ride.requested` as usual.
3. It fails with a reason, and notifies the rider, if the passenger already has an active ride or the account is
   suspended.

**Notifications.** A reminder at `pickup_at − 60 min` and a notification at dispatch, through the existing
notification pipeline.

**Cancellation.** Free while SCHEDULED. Once dispatched, the normal ride rules apply.

**Honesty.**
- The final fare is still computed from the GPS-measured distance, as for every ride.
- The locked price is the estimate, not a guarantee, and the UI says so.

**Driver pre-acceptance: deferred.** Design only:
- a `scheduled_ride_reservations` table;
- a driver may reserve a scheduled ride up to 24 h ahead;
- at dispatch, the reserved driver is offered the ride first for 60 s before normal matching.

It is deferred because it changes matching fairness and needs a no-show policy.

## 8. Categories: bike and auto (P1)

- `vehicles.category` and `rides.category` gain BIKE and AUTO (migration updates the CHECK constraints).
- Pricing, seat rules (BIKE 2, AUTO 3 or 4) and labels live in configuration.
- The prices are **illustrative demo tariffs**, documented as such.
- The demo seed gains bike and auto drivers; the simulator reads the vehicle category from the API.

## 9. Driver command center (P1)

**Data.** `driver_online_sessions`
- Columns: `id`, `driver_id fk`, `started_at`, `ended_at null`, `end_reason` (OFFLINE, LOCATION_TIMEOUT or
  SUSPENDED).
- Partial unique index: one open session per driver.
- Written by the availability service and the presence sweeper.

**API.** `GET /api/drivers/me/stats?from&to` returns, from real aggregates:
- earnings, trips, online seconds, earnings per online hour;
- offers received and accepted, acceptance rate (null below 5 offers);
- driver cancellations and cancellation rate (null below 5 accepted rides);
- rating average and count.

The UI compares today with the daily average of the previous 7 days, and shows counts next to every rate.

**Demand map.** `GET /api/drivers/demand?lat&lng`, for drivers only.
- **Cells:** geohash-6 cells within 5 km.
- **Demand:** requests in the last 30 minutes, from `rides`.
- **Supply:** fresh available drivers.
- **Level:** LOW, NORMAL or HIGH from the demand-to-supply ratio.
- **Anonymity:** cells with fewer than 3 requests are omitted. Only levels are returned, never counts.
- **Cache:** Redis `demand:{geohash5}`, 60 s.

**Driver trip history.** A new page over the existing `GET /api/rides`.

## 10. Mobility Copilot (P2)

**API.**
- `POST /api/copilot/ask {question, quoteToken?}` returns `{intent, answer, facts[], generatedBy: RULES | MODEL}`.
- Rate limit `COPILOT`: 20 per hour.

**Pipeline.**
1. **Intent** by deterministic rules. The intents are:
   - SPEND_PERIOD
   - SPEND_PATTERN
   - TRIP_COMPARE
   - FARE_CHANGE
   - CATEGORY_ADVICE
   - CHEAPER_OPTION
   - LEAVE_TIMING
   - UNSUPPORTED
2. **Facts** from `UserMobilitySummaryService`: SQL aggregates over the user's own completed rides and captured
   payments (spend by month, weekday and hour, category mix, typical distance and fare), plus `TripFacts` for the
   last ride and `MobilityFacts` from a signed fare quote passed by the client.
3. **Answer** built from a template per intent: the facts, in words.
4. When AI is enabled, the model may rephrase that answer. It goes through the existing number validator: every
   number must be one of the facts, otherwise the template answer is returned.

**Honesty rules.**
- LEAVE_TIMING answers with the current route estimate and surge, and states that Raido has no traffic forecast.
- UNSUPPORTED lists what the Copilot can answer.
- With fewer than 3 completed rides, pattern questions say there is not enough history.

## 11. Best way to go and smart pickup (P2)

**Best way to go.**
- For the chosen places, compare every enabled category from one estimate call (real quotes).
- Add *walk* when the route is under 2.5 km: route distance ÷ 1.3 m/s, labelled as an estimate.
- Show transit as *not available in Raido* (no data source).

**Smart pickup.** `GET /api/geo/pickup-suggestion?lat&lng`
- Uses the routing service's `nearest` call to snap the point to a drivable road.
- Suggests it when it is more than 25 m away, with the distance and the road name, and the reason *closer to a
  road a car can reach*.
- With straight-line routing, no suggestion is returned.
- No safety claims are made.

## 12. Price tiers (P2)

Configuration `rideflow.pricing.tiers`:
- *Save*: a discount on the subtotal, and matching that starts after a delay in the first-round radius only.
- *Standard*: today's behaviour.
- *Fast*: a priority fee, and a first round that uses the round-2 radius and sends 5 offers.

Tiers are off unless configured. The quote carries the tier, so the price and the matching behaviour come from the
same signed quote.

## 13. Vehicle capabilities and ride preferences (P2)

- `vehicle_capabilities (vehicle_id, capability)` with PET_FRIENDLY, EXTRA_LUGGAGE and WHEELCHAIR_ACCESSIBLE,
  declared by the driver and verified with the vehicle by an admin.
- `ride_preferences (ride_id, capability)`. Matching filters on declared capabilities.
- The UI says "declared by the driver"; Raido does not certify accessibility.

## 14. Support center (P2)

**Data.**
- `support_tickets`
  - Columns: `id`, `user_id fk`, `ride_id fk null`, `category` (RIDE, PAYMENT, SAFETY, DRIVER, ACCOUNT or
    TECHNICAL), `priority` (LOW, NORMAL, HIGH or URGENT), `status` (OPEN, IN_PROGRESS, RESOLVED or CLOSED),
    `subject`, `created_at`, `updated_at`, `resolved_at`.
  - Index `(status, priority, created_at)`.
- `support_messages`: `id`, `ticket_id fk`, `author_id`, `author_role`, `body varchar(2000)`, `created_at`.

**Rules.**
- SAFETY tickets start at HIGH.
- A ride can be referenced only by one of its participants.

**API.**
- User: `/api/support/tickets` (list, create, view, reply).
- Admin: `/api/admin/support/tickets` (filter, assign status, reply).

## 15. Admin operations (P2)

- **Queues.** A safety queue (`/api/admin/safety-events`) and a support queue.
- **Ride funnel.** `/api/admin/funnel?from&to`: rides requested, offered, accepted, arrived, started and completed,
  and offer outcomes, from `rides` and `ride_offers`.
- **Live map.** `/api/admin/live`: active rides with exact driver positions (admins only).
- **Admin cancel.** `POST /api/admin/rides/{id}/cancel {reason}`, allowed from any non-terminal state including
  IN_PROGRESS, with actor ADMIN. It is audited, the driver is released, and the payment is not settled.
- **Incident timeline.** Status events, offers, safety events and chat metadata (counts, not bodies) merged in
  time order.

## 16. Deferred designs

| Feature | Design sketch |
|---|---|
| Multi-stop | `ride_stops (ride_id, seq, lat, lng, label, arrived_at)`. The quote covers the full path. The state machine gains per-stop arrival events inside IN_PROGRESS |
| Shared rides | Pool matching on route overlap, a seat count per ride, and a fare split. Needs a matching model beyond nearest driver |
| Parcel | A separate `deliveries` aggregate with sender, recipient, package size, a pickup and delivery PIN and a proof photo. It shares drivers, matching and tracking, but never the `Ride` entity |
| Women-preference matching | Opt-in for both riders and drivers, enabled per market by configuration, a best-effort filter in the first matching rounds only, with the wait-time impact disclosed. Gender is self-declared and stored separately under access control. Requires legal review before being turned on |
| Cancellation fees | A free window after acceptance, then a fee added to the next payment. Requires real payments |
