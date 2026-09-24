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
| GET | `/geo/search?q=&lat=&lng=` | Place search biased near a point → `[{label, point}]` |
| GET | `/geo/reverse?lat=&lng=` | Address label for a point |
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
| GET | `/rides/{id}/tracking` | Latest driver location snapshot `{point, headingDeg, recordedAt, stale, etaSeconds}` *(Phase 4)* |
| GET | `/rides/{id}/timeline` | Status events |
| POST | `/rides/{id}/cancel` | Optional body `{reason}`. Passenger (before the trip starts) → `CANCELLED`, and the driver is released. Assigned driver before arrival → ride goes back to `MATCHING` and is re-offered to other drivers. Driver after arriving → `CANCELLED` only once the 5-minute no-show wait has passed, else `409 NO_SHOW_WAIT_NOT_ELAPSED` |
| POST | `/rides/{id}/rating` | `{score 1-5, comment?}` → 201; only after `COMPLETED`, once per rater *(Phase 6)* |

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
  "payment": null,
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
| POST | `/drivers/location` | DRIVER | `{location{lat,lng}, headingDeg?, speedMps?, accuracyMeters?, recordedAt}` → 202. `recordedAt` must be within 30 s in the past / 5 s in the future (`422 STALE_LOCATION`); driver must be online (`409 DRIVER_OFFLINE`). REST fallback for the WebSocket stream (Phase 4) |
| GET | `/drivers/me/offers` | DRIVER | Pending offers (used on reconnect) |
| GET | `/drivers/me/earnings?from=&to=&granularity=DAY` | DRIVER | `{total, tripCount, onlineSeconds?, series[{bucket, earnings, trips}]}` from `payments` |
| GET | `/drivers/nearby?lat=&lng=&radiusMeters=&category=` | PASSENGER, ADMIN | Passenger: `[{point (≈100 m grid), category}]`, max 20, no identity. Admin: full detail |

## Trips: AI Trip Intelligence

| Method | Path | Description |
|---|---|---|
| GET | `/trips/{id}/ai-analysis` | Analysis for a completed ride the caller took part in |
| POST | `/trips/{id}/ai-analysis/regenerate` | Re-run if `FAILED`/`UNAVAILABLE` (rate limited) → 202 |
| POST | `/trips/{id}/ai-analysis/questions` | `{question ≤ 500 chars}` → answer, synchronous with timeout |
| GET | `/trips/{id}/ai-analysis/questions` | Q&A history |

```json
// GET /trips/{id}/ai-analysis
{
  "rideId": "…", "status": "COMPLETED",              // PENDING | COMPLETED | FAILED | UNAVAILABLE
  "failureCode": null,
  "observations": [                                   // deterministic, computed by backend; always present
    { "key": "surge.applied", "text": "A 1.2× demand multiplier was applied at booking time." }
  ],
  "insights": {                                       // AI output, validated; null unless COMPLETED
    "summary": "…", "fareExplanation": "…",
    "observations": [ { "type": "FARE", "text": "…" } ],
    "recommendations": [ "…" ],
    "comparison": "…"                                 // null when < 3 prior trips
  },
  "provider": "local", "model": "…", "promptVersion": "trip-analysis/v1",
  "generatedAt": "…"
}

// POST /trips/{id}/ai-analysis/questions → 200
{ "answerable": true, "answer": "…", "factKeysUsed": ["fare.final.total", "history.avgFare"] }
// → 503 AI_UNAVAILABLE when provider disabled / circuit open; 429 when rate limited
```

## Notifications

| Method | Path |
|---|---|
| GET | `/notifications?unreadOnly=true&page=&size=` |
| POST | `/notifications/{id}/read` → 204 |
| POST | `/notifications/read-all` → 204 |

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
