# Current status (verified 2026-09-26)

What works, checked by running it: the whole stack on one machine, driven in a real browser (Chromium through
Playwright, plus the in-app browser), with console errors and failed requests recorded on every page. Nothing
here is taken from the README or older documents.

## How it was run

No Docker is available on the development machine, so the stack ran from official portable builds in a scratch
folder, isolated from the owner's cloud accounts. The owner's local backend points at their Neon database, and
none of this touched it.

| Part | Version | Where |
|---|---|---|
| PostgreSQL + PostGIS | 17.6 + 3.6.2 | localhost:55432 |
| Redis | 5.0.14 (Windows build) | localhost:56379 |
| Kafka (KRaft, single node) | 4.2.1 | localhost:59092 |
| Backend | branch `feature/raido-product`, jar, profile `demo` | localhost:8090 |
| Frontend | production build (`next build` + `next start`) | localhost:3459 |
| Driver simulator | 5 drivers, CI speed settings (60 m/s) | against the backend |

Real external services were used as in production: OpenFreeMap tiles, the public OSRM router and Nominatim.

## WORKING (verified)

| Area | Evidence |
|---|---|
| Startup | Flyway applied V1–V8 and the demo seed to an empty database in 1.1 s; the backend was ready in 20 s |
| Home, sign-in, registration pages | Load with no console errors and no failed requests (after fix 2 below) |
| Registration and sign-in | New passenger and new driver registered through the UI; demo accounts signed in (e2e `smoke`, `driver`) |
| Session refresh and sign-out | The account menu signs out; `/ride` then redirects to `/login?next=/ride` |
| Passenger booking | Pickup and destination by map click and by device location, a fare estimate with real routes, request (e2e `smoke`, `passenger`) |
| Matching and realtime ride states | A simulated driver accepted, drove, arrived, started and completed; the passenger's screen moved through every state from WebSocket pushes, without reloading (e2e `smoke`) |
| Live driver position | The car moved on the passenger's map from `/user/queue/ride-location` pushes, with a live ETA (screenshot `passenger-live-ride`) |
| Map | Draws in the production build at 320, 375, 390, 430, 768, 1024 and 1440 px; roads, labels, the route, the live car, pickup and destination pins |
| Cancellation | While matching, with a reason; it shows in trip history (e2e `passenger`) |
| Trip completion, rating, trip history, trip details | Final fare, 5-star rating, trip details with the fare breakdown and trip observations (e2e `smoke`) |
| Driver journey | Onboarding, admin verification, going online from GPS, an offer over the socket, accept, en route, arrive (geofence), start, complete, rating the passenger, earnings (e2e `driver`, 1.1 min) |
| Admin console | Overview, rides, drivers, users, system, audit log: all load for an admin with no errors |
| WebSocket recovery | With a passenger signed in, the backend was stopped: the indicator turned Offline and the banner said "Reconnecting… live updates are paused." When it came back, the client reconnected on its own, showed "Connection restored." and cleared it about 4 s later |
| Responsive layout | No horizontal overflow at any width tested; bottom sheet up to 768 px, floating panel from 1024 px |
| Redis | Driver state, route and geocode caches, and rate limits live in Redis |
| Kafka | Outbox, matching, notifications, payments, trip analysis and the realtime bridge all ran through the local broker |

## FIXED while verifying

1. **Every route was a straight line** (critical). Against the public OSRM server, every route request failed with
   `ZipException: incorrect header check` and fell back to a straight-line estimate, so fares, ETAs and the route
   drawn on the map were approximations.
   - **Cause:** Spring 7's JDK request factory offers gzip and deflate. Offered deflate, the router labels its reply
     `deflate` but sends gzip bytes.
   - **Fix:** the OSRM and Nominatim clients turn that negotiation off (commit 178d49a).
   - **Tests:** `OutboundHttpTest` reproduces the router's behaviour and fails without the fix.
   - **Verified:** the real router now returns a 32-point, 14.4 km route across Hyderabad.
2. **Every first-time visitor got a 401 console error**, from the session check on page load. With no refresh cookie
   the endpoint now answers 204 (not signed in); a bad cookie is still 401. The re-audit showed no console errors
   on any page (commit 8e17d55).
3. **The pending-driver page had no heading**, and **the header clipped "Trips" at 320 px** (commit 8e17d55).

## PARTIALLY WORKING

| Area | What is and is not verified |
|---|---|
| AI Trip Intelligence | With AI disabled (the deployed configuration), trip details show the deterministic observations and say the AI summary is unavailable. A model-written analysis was not run here: there is no local model and no API key. The model path is covered in CI by `AITripInsightsIT` and `AIFailureMatrixTest` against WireMock stand-ins |
| Redis health indicator | Reports DOWN against the Windows Redis 5 build only (its `INFO` output contains a Windows path Spring cannot parse). The data paths work. Not expected with Upstash or `redis:8` |

## BROKEN

| Area | Detail |
|---|---|
| The deployed site | `https://frontend-seven-henna-61.vercel.app` answers `DEPLOYMENT_NOT_FOUND` for every page. The latest Vercel builds of `main` failed: `main` is still at Phase 14, whose build rejected a missing backend URL. That was fixed on `phase-15` (f906db6), which has not been merged to `main` |
| The deployed backend | Not verifiable: no public backend URL has been provided |

## Not reproduced

- One registration in the e2e run stalled for more than 5 s. Registration otherwise takes 0.33 s through the proxy,
  and the same test passed on rerun.
- One renderer crash during a long page-audit run. Replaying the same pages while sampling memory stayed at
  13–27 MB with no crash.

## NOT IMPLEMENTED (from the Raido brief)

- **Built in parts:** in-ride chat is built (R3). The Safety Center backend is started on the local branch
  `wip/r6-safety`: it compiles, but has no API or tests.
- **Not started:**
  - saved places, ride again, scheduled rides;
  - driver command-center metrics (acceptance, online time) and the demand map;
  - Mobility Copilot, "best way to go", bike and auto categories;
  - support center, admin safety and support queues;
  - the redesigned public website.

See [product-roadmap.md](product-roadmap.md).
