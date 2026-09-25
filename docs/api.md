# RideFlow — REST API Contracts

Base path `/api`. JSON only. Interactive docs are generated at `/swagger-ui.html` from the OpenAPI spec (`/v3/api-docs`). The frontend's TypeScript types are generated from that same spec.

## Conventions

- **Auth:** `Authorization: Bearer <accessToken>` unless marked *public*.
- **Success bodies:** return the resource directly, with no envelope. Lists use `PageResponse`.
- **IDs:** UUID strings. **Timestamps:** ISO-8601 UTC. **Money:** `{ "amount": "245.50", "currency": "INR" }` (amount as a string to avoid float rounding in JS).
- **Coordinates:** `{ "lat": 17.4435, "lng": 78.3772 }`, validated `lat ∈ [-90,90]`, `lng ∈ [-180,180]`.
- **Pagination:** `?page=0&size=20&sort=requestedAt,desc`. `size` ≤ 100. `sort` fields are allow-listed per endpoint; anything else → `400 INVALID_SORT_FIELD`.

```json
// PageResponse<T>
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7, "sort": "requestedAt,desc" }
```

- **Errors:** always `ApiError` (see [architecture.md §14](architecture.md#14-error-handling)).

| Status | Used for |
|---|---|
| 200 / 201 / 204 | success / created (with `Location` header) / no content |
| 400 | malformed request, validation failure (`VALIDATION_FAILED` + `fieldErrors`) |
| 401 | missing/invalid/expired token (`UNAUTHENTICATED`, `INVALID_TOKEN`, `INVALID_CREDENTIALS`, `SESSION_REVOKED`) |
| 403 | authenticated but not allowed (`FORBIDDEN`, `ACCOUNT_SUSPENDED`, `CSRF_HEADER_MISSING`, `DRIVER_NOT_VERIFIED`) |
| 404 | not found **or not visible to caller** |
| 409 | state conflict (`RIDE_INVALID_TRANSITION`, `ACTIVE_RIDE_EXISTS`, `RIDE_ALREADY_ASSIGNED`, `OFFER_EXPIRED`, `EMAIL_TAKEN`, `PHONE_TAKEN`, `LICENSE_TAKEN`, `PLATE_TAKEN`, `DRIVER_PROFILE_EXISTS`, `INVALID_DRIVER_STATE`, `INVALID_USER_STATE`, `CONCURRENT_MODIFICATION`) |
| 422 | semantically invalid (`OUTSIDE_SERVICE_AREA`, `PICKUP_EQUALS_DROPOFF`, `QUOTE_EXPIRED`, `NOT_AT_PICKUP`, `WRONG_CURRENT_PASSWORD`, `INVALID_MODEL_YEAR`) |
| 429 | rate limited (`RATE_LIMITED`, `Retry-After` header) |
| 503 | optional dependency unavailable (`AI_UNAVAILABLE`) |

---

## Auth

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Create a PASSENGER or DRIVER account |
| POST | `/auth/login` | public | Issue access token + refresh cookie |
| POST | `/auth/refresh` | refresh cookie | Rotate refresh token, new access token |
| POST | `/auth/logout` | refresh cookie | Revoke refresh token family → 204 |

```json
// POST /auth/register
{ "email": "asha@example.com", "password": "S3cure-pass!", "fullName": "Asha Rao",
  "phone": "+919876543210", "accountType": "PASSENGER" }        // accountType: PASSENGER | DRIVER
// 201 → UserResponse

// POST /auth/login
{ "email": "asha@example.com", "password": "S3cure-pass!" }
// 200
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900,
  "user": { "id": "…", "email": "…", "fullName": "…", "role": "PASSENGER" } }
// + Set-Cookie: rf_refresh=…; HttpOnly; Secure; SameSite=Lax; Path=/api/auth
```

Password policy: 10–72 characters (BCrypt limit), at least one letter and one digit. `/auth/refresh` and `/auth/logout` require the `X-Requested-With: rideflow` header (CSRF defence for the cookie-based endpoints).

## Users

| Method | Path | Access |
|---|---|---|
| GET | `/users/me` | any |
| PATCH | `/users/me` | any (`fullName`, `phone`) |
| PUT | `/users/me/password` | any (`currentPassword`, `newPassword`), revokes other sessions |

## Geo (backend-proxied providers)

| Method | Path | Description |
|---|---|---|
| GET | `/geo/search?q=&lat=&lng=` | Place search (`q` 2–200 chars), biased towards `lat`/`lng` when given → `[{name, address, point}]`. Call on submit, not per keystroke (Nominatim policy). `429 RATE_LIMITED` (30/min per user), `503 GEOCODING_UNAVAILABLE` with `Retry-After` when the provider is down or the shared upstream budget (1 req/s) is used up |
| GET | `/geo/reverse?lat=&lng=` | Address at a point → `{name, address, point}`, or `204` when there is none. Same limits |
| GET | `/geo/route?fromLat=&fromLng=&toLat=&toLng=` | `{distanceMeters, durationSeconds, geometry (GeoJSON LineString), source}`; used to draw routes and by the simulator |

## Fares

`POST /fares/estimate` (PASSENGER)

```json
// request
{ "pickup": { "lat": 17.4435, "lng": 78.3772 }, "dropoff": { "lat": 17.4239, "lng": 78.4738 } }
// 200
{
  "distanceMeters": 12840, "durationSeconds": 1720, "estimateSource": "ROUTED",
  "surgeMultiplier": "1.2",
  "quotes": [
    {
      "quoteId": "3f6c…", "vehicleCategory": "ECONOMY",
      "estimatedFare": { "amount": "286.00", "currency": "INR" },
      "minimumFare":   { "amount": "80.00",  "currency": "INR" },
      "breakdown": { "baseFare": "40.00", "distanceCharge": "154.08", "timeCharge": "28.67",
                     "subtotal": "222.75", "surgeMultiplier": "1.2", "bookingFee": "19.00",
                     "minimumFareApplied": false, "total": "286.00" },
      "expiresAt": "2026-09-24T10:20:30Z"
    }
  ]
}
```

(The numbers above only illustrate the shape. Real values come from configuration and routing.) The response also contains `route`, the path to draw, as `[{lat, lng}]`.

`quoteId` is an HMAC-signed token, not a database id: it carries the category, coordinates, route estimate, surge and full fare breakdown, is bound to the requesting passenger, and expires after 5 minutes (`rideflow.ride.quote-ttl`). Errors at booking time: `QUOTE_INVALID` (tampered, or issued to someone else), `QUOTE_EXPIRED`, `QUOTE_MISMATCH` (pickup/dropoff moved more than 50 m).

## Rides: passenger

| Method | Path | Description |
|---|---|---|
| POST | `/rides` | Create ride from a quote → `201 RideResponse` (status `REQUESTED`) |
| GET | `/rides` | Caller's rides (passenger: own; driver: assigned). Filters: `status`, `from`, `to`. Sort: `requestedAt`, `completedAt` |
| GET | `/rides/active` | Caller's active ride or `204` |
| GET | `/rides/{id}` | Ride detail |
| GET | `/rides/{id}/tracking` | Snapshot after load or reconnect: `{rideId, status, driverLocation{point, headingDeg, recordedAt}?, stale, eta{target: PICKUP\|DROPOFF, seconds, distanceMeters, source: ROUTED\|APPROXIMATE}?}`. Passenger or assigned driver; a driver who only got an offer gets 404. `409 TRACKING_UNAVAILABLE` unless a driver is assigned (DRIVER_ASSIGNED to IN_PROGRESS). `stale` when the last position is older than 30 s; `eta` is `null` when stale or while the driver waits at the pickup. Live updates then arrive over WebSocket ([events.md](events.md) §2) |
| GET | `/rides/{id}/timeline` | Status events |
| POST | `/rides/{id}/cancel` | Optional body `{reason}`. Passenger (before the trip starts) → `CANCELLED`, and the driver is released. Assigned driver before arrival → ride goes back to `MATCHING` and is re-offered to other drivers. Driver after arriving → `CANCELLED` only once the 5-minute no-show wait has passed, else `409 NO_SHOW_WAIT_NOT_ELAPSED` |
| POST | `/rides/{id}/rating` | `{score 1-5, comment? (≤ 500)}` → `201 {id, rideId, score, comment, createdAt}`. The passenger rates the driver, the driver rates the passenger. `409 RIDE_NOT_COMPLETED` before completion, `409 ALREADY_RATED` on a second rating, `404` for anyone else. The driver's `ratingAvg`/`ratingCount` update immediately |

```json
// POST /rides
{ "quoteId": "3f6c…",
  "pickup":  { "point": { "lat": 17.4435, "lng": 78.3772 }, "address": "Hitech City Metro" },
  "dropoff": { "point": { "lat": 17.4239, "lng": 78.4738 }, "address": "Hussain Sagar" },
  "paymentMethod": "CASH" }
```

The quote pins category, distance/time estimate and surge. Its pickup/dropoff must match the request within 50 m, otherwise `422 QUOTE_MISMATCH`.

```json
// RideResponse
{
  "id": "…", "status": "DRIVER_ARRIVING", "version": 5, "vehicleCategory": "ECONOMY",
  "pickup":  { "point": {...}, "address": "…" },
  "dropoff": { "point": {...}, "address": "…" },
  "estimate": { "distanceMeters": 12840, "durationSeconds": 1720, "source": "ROUTED",
                "fare": { "amount": "286.00", "currency": "INR" }, "breakdown": {...} },
  "final": null,
  "driver":  { "id": "…", "fullName": "…", "ratingAvg": "4.86",
               "vehicle": { "make": "…", "model": "…", "color": "…", "plateNumber": "…" } },
  "payment": null,   // after completion, once settled: { "id", "method", "status", "provider": "CASH|SANDBOX", "amount": { "amount", "currency" } }
  "requestedAt": "…", "acceptedAt": "…", "startedAt": null, "completedAt": null,
  "cancellation": null
}
```

## Rides: driver actions

All require role DRIVER, the ride must be assigned to (or offered to) the caller, and they return `200 RideResponse`.

| Method | Path | Transition / rule |
|---|---|---|
| POST | `/rides/{id}/accept` | `MATCHING → DRIVER_ASSIGNED`; needs a PENDING, unexpired offer; `409 RIDE_ALREADY_ASSIGNED` if another driver won |
| POST | `/rides/{id}/reject` | offer → REJECTED (ride unchanged) → `204` |
| POST | `/rides/{id}/en-route` | `DRIVER_ASSIGNED → DRIVER_ARRIVING` |
| POST | `/rides/{id}/arrive` | `DRIVER_ARRIVING → DRIVER_ARRIVED`; driver's latest location within pickup geofence, otherwise `422 NOT_AT_PICKUP` |
| POST | `/rides/{id}/start` | `DRIVER_ARRIVED → IN_PROGRESS` |
| POST | `/rides/{id}/complete` | `IN_PROGRESS → COMPLETED`. Distance = PostGIS length of the recorded GPS trail (`distanceSource: TRACKED`), or the routed estimate when the trail has fewer than two points (`ESTIMATED`). The final fare uses the surge locked at booking |

Errors common to driver actions: `404 RIDE_NOT_FOUND` (not your ride or offer), `409 RIDE_INVALID_TRANSITION`, `409 OFFER_EXPIRED`, `409 DRIVER_UNAVAILABLE`, `422 LOCATION_UNAVAILABLE` (no fresh GPS position for the geofence check).

## Drivers

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/drivers/me/profile` | DRIVER | Onboarding: `{licenseNumber, vehicle{make, model, color, plateNumber, modelYear, category, seats}}` → `PENDING` verification |
| GET | `/drivers/me` | DRIVER | Profile, verification, availability, vehicle, rating |
| PUT | `/drivers/me/vehicle` | DRIVER | Replace active vehicle (re-verification not required in v1) |
| POST | `/drivers/online` | DRIVER (VERIFIED) | `{location{lat,lng}}` → `AVAILABLE` (`403 DRIVER_NOT_VERIFIED`, `409 NO_ACTIVE_VEHICLE`) |
| POST | `/drivers/offline` | DRIVER | `→ OFFLINE`, releases any pending offer; `409 DRIVER_ON_TRIP` during a trip. Returns the driver profile |
| POST | `/drivers/location` | DRIVER | `{location{lat,lng}, headingDeg?, speedMps?, accuracyMeters?, recordedAt}` → 202. `recordedAt` must be within 30 s in the past / 5 s in the future (`422 STALE_LOCATION`); driver must be online (`409 DRIVER_OFFLINE`). REST fallback for the WebSocket stream `/app/drivers/location`, with the same rules and the same push to the passenger |
| GET | `/drivers/me/offers` | DRIVER | Pending offers (used on reconnect) |
| GET | `/drivers/me/earnings?from=&to=&granularity=DAY` | DRIVER | `{total, tripCount, onlineSeconds?, series[{bucket, earnings, trips}]}` from `payments` |
| GET | `/drivers/nearby?lat=&lng=&radiusMeters=&category=` | PASSENGER, ADMIN | Passenger: `[{point (≈100 m grid), category}]`, max 20, no identity. Admin: full detail |

## Trips: AI Trip Intelligence

Passenger of the ride only (drivers 403, other passengers 404), and only once the ride is `COMPLETED` (else `409 RIDE_NOT_COMPLETED`). Design: [architecture.md](architecture.md) §12, [ai.md](ai.md).

| Method | Path | Description |
|---|---|---|
| GET | `/trips/{id}/ai-analysis` | Computed observations, plus the AI insights once ready. `PENDING` right after completion (observations already present) |
| POST | `/trips/{id}/ai-analysis/regenerate` | Re-run a `FAILED`, `UNAVAILABLE` or abandoned (`PENDING` > 15 min) analysis in the background → `202` with the `PENDING` analysis. `409 AI_ANALYSIS_NOT_REGENERABLE` otherwise; `429` after 3 per hour |
| POST | `/trips/{id}/ai-analysis/questions` | `{question: 1–500 chars}` → answer, synchronous. `503 AI_UNAVAILABLE` when the provider is disabled, down, too slow or gave no valid answer; `429` after 10 per hour |
| GET | `/trips/{id}/ai-analysis/questions` | The caller's questions about this trip, oldest first, including failed ones |

```json
// GET /trips/{id}/ai-analysis
{
  "rideId": "…", "status": "COMPLETED",              // PENDING | COMPLETED | FAILED | UNAVAILABLE
  "failureCode": null,                                // TIMEOUT | PROVIDER_ERROR | RATE_LIMITED | INVALID_RESPONSE | REFUSED | BUSY | UNAVAILABLE
  "observations": [                                   // computed by the backend; always present
    { "key": "surge.applied", "text": "A demand multiplier of 1.2× was locked in at booking time; it applies equally to the estimate and the final fare." }
  ],
  "insights": {                                       // validated AI output; null unless COMPLETED
    "summary": "…", "fareExplanation": "…",
    "observations": [ { "type": "FARE", "text": "…" } ],   // FARE | ROUTE | TIME | COMPARISON | OTHER
    "recommendations": [ "…" ],
    "comparison": null,                               // null unless ≥ 3 earlier trips
    "factKeysUsed": [ "fare.final.total", "distance.actualKm" ]
  },
  "provider": "local", "model": "…", "promptVersion": "trip-analysis/v1",
  "updatedAt": "…"
}

// POST /trips/{id}/ai-analysis/questions → 200
{ "id": "…", "question": "Why did I pay more than the estimate?", "status": "COMPLETED", "failureCode": null,
  "answerable": true, "answer": "…", "factKeysUsed": ["fare.final.total", "distance.actualKm"], "askedAt": "…" }
```

## Notifications

Any authenticated user; always the caller's own notifications.

| Method | Path | Description |
|---|---|---|
| GET | `/notifications?unreadOnly=&page=&size=&sort=createdAt,desc` | `PageResponse` of `{id, type, title, body, rideId?, read, createdAt}` |
| POST | `/notifications/{id}/read` | `204`; another user's id gives `404 NOTIFICATION_NOT_FOUND` |
| POST | `/notifications/read-all` | `204` |

Notifications are created asynchronously by the notifications consumer (ride progress, payments, driver verification decisions) and pushed to `/user/queue/notifications` ([events.md](events.md) §2.3).

## Admin (role ADMIN)

| Method | Path | Description |
|---|---|---|
| GET | `/admin/overview?from=&to=` | Ride counts by status, completion and cancellation rate, median time-to-match, gross fares, drivers online (DB + Redis) |
| GET | `/admin/analytics/rides?from=&to=&granularity=HOUR\|DAY` | Time series: requested, completed, cancelled, expired, revenue |
| GET | `/admin/users?role=&status=&q=` | Paged user search |
| PATCH | `/admin/users/{id}/status` | `{status: ACTIVE\|SUSPENDED, reason}` (suspension revokes sessions) |
| GET | `/admin/drivers?verificationStatus=&availability=` | Paged drivers |
| POST | `/admin/drivers/{id}/verify` | → `VERIFIED`, emits `notification.requested` |
| POST | `/admin/drivers/{id}/reject` | `{reason}` → `REJECTED` |
| POST | `/admin/drivers/{id}/suspend` | `{reason}` → `SUSPENDED`, forces `OFFLINE` |
| GET | `/admin/rides?status=&from=&to=&passengerId=&driverId=` | Paged rides |
| GET | `/admin/rides/{id}` | Ride + timeline + offers + payment + analysis status |
| GET | `/admin/system` | Actuator health components, outbox backlog, DLT counts, AI circuit-breaker state, WebSocket sessions (live values from `MeterRegistry` / health indicators) |
| GET | `/admin/audit-logs?action=&entityType=&from=&to=` | Paged audit log |

## Operational (management port, not public)

`/actuator/health` (liveness/readiness groups), `/actuator/info`, `/actuator/prometheus`.
