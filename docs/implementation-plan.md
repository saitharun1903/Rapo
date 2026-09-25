# RideFlow — Implementation Plan

Each phase ends with: compile → tests → lint/static checks → self-review → docs update. Nothing is marked done without being run.

## Environment prerequisites (found during Phase 1 inspection)

| Tool | Status on dev machine | Action |
|---|---|---|
| Java 21 | ✅ 21.0.7 | none |
| Node.js | ✅ v24 | none |
| Git | ✅ 2.50 | repo initialised |
| Maven | ❌ not installed | not needed: the project ships the Maven Wrapper (`mvnw`) |
| Docker | ❌ not installed | **required from Phase 2** for PostGIS/Redis/Kafka and Testcontainers. Install Docker Desktop (WSL 2 backend). CI runners have Docker, so integration tests always run there |
| Location | repo is inside OneDrive | recommended to move to a non-synced path (e.g. `C:\dev\rideflow`): OneDrive syncing `node_modules`/`target` is slow and can lock files |

## Target repository layout

```
/
├── backend/                 Spring Boot (Maven wrapper)
├── frontend/                Next.js
├── simulator/               demo-only driver simulator (TypeScript)
├── infrastructure/
│   ├── docker/              Dockerfiles, init scripts
│   ├── prometheus/          prometheus.yml
│   └── grafana/             provisioning + dashboards JSON
├── load-tests/              k6 scripts + results/ (gitignored raw output)
├── docs/
├── .github/workflows/       backend-ci.yml, frontend-ci.yml, docker.yml
├── docker-compose.yml       default: infra, apps, Prometheus, Grafana; profile demo: simulator
├── .env.example
├── README.md
└── LICENSE
```

## Progress

| Phase | Status | Evidence / open items |
|---|---|---|
| 1 Architecture | ✅ Approved | This document set |
| 2 Backend foundation | ✅ Verified | GitHub Actions `backend-ci` run 36032457542: unit, web-slice, ArchUnit and all 13 Testcontainers integration tests passed against real PostGIS (the CI step fails if any integration test is skipped) |
| 3 Ride system | ✅ Verified | GitHub Actions `backend-ci` run 36036099948: 120 unit/web/ArchUnit tests and 29 PostGIS integration tests passed (full ride lifecycle over HTTP, three-driver simultaneous accept race, offer expiry with radius growth, ride expiry, re-dispatch, quote tampering/expiry, proximity query correctness and GiST index use) |
| 4 Real-time | ✅ Verified | GitHub Actions `backend-ci` run 36040434543: 153 unit/web/ArchUnit tests and 35 PostGIS integration tests passed, including `RealtimeIT` with a real STOMP client against the running server (offer, status and driver location reach only the ride's participants; offers withdrawn from losing drivers; unauthenticated, forged-token, foreign-origin, admin-topic, foreign-queue and spoofed-send frames refused with ERROR and closed; invalid location messages answered without closing; flood throttling; silent drivers taken offline while drivers on a trip are not; sockets closed at token expiry) |
| 5 Redis | ✅ Verified | GitHub Actions `backend-ci` run 36044436417: 175 unit/web/ArchUnit tests and 42 integration tests passed against real PostGIS and Redis (`RedisCachingIT`: TTLs, fallback routes not cached, hashed keys, ETA eviction on status change; `RateLimitIT`: login per IP and email, register per IP, 429 with `Retry-After`, shared geocoder budget). `cache-benchmark` run 36044436543: before/after k6 numbers in [performance.md](performance.md) |
| 6 Kafka | ✅ Verified | GitHub Actions `backend-ci` run 36050446683: 200 unit/web/ArchUnit tests and all integration tests (49 test methods, 0 skipped) passed against real PostGIS, Redis and Kafka. `KafkaEventFlowIT` checks each exit criterion. **Full lifecycle over Kafka:** every ride step read back from its topic by an independent consumer, keyed by ride and in `aggregateVersion` order, then payment and notifications. **DLT:** an undecodable record is dead-lettered at once by each group that reads it; a failing handler is retried 1 s, 2 s, 4 s and then dead-lettered. **Redelivery:** the same `ride.completed` record delivered twice more creates no second payment or notification. The earlier run 36050273822 failed only on a wrong test expectation, corrected in 74574a2 (see below) |
| 7 AI | ✅ Verified | GitHub Actions `backend-ci` run 36097403045: 237 unit/web/ArchUnit tests and all integration tests (53 test methods, 0 skipped) passed. **Failure matrix:** `AIFailureMatrixTest` runs both providers against WireMock (success, 5xx/529 retried, 4xx not retried, `Retry-After` honoured only up to 10 s, slow headers and slowly dribbled bodies time out, invalid output with one corrective retry, invented numbers, refusal, circuit opening, bulkhead full, provider disabled). **Ride completion unaffected:** `AITripInsightsIT` completes a ride with a failing provider and checks it is still completed and paid, the analysis is `FAILED(PROVIDER_ERROR)`, and regenerate then succeeds; it also checks passenger-only access and that stored facts hold no personal data. **Real local model:** five recorded runs with codegemma and codeqwen in [ai.md](ai.md) §5. The earlier run 36096846204 failed only because `KafkaEventFlowIT` did not yet expect the third `ride.completed` group, corrected in b3ffbc9 |
| 8 Frontend | ✅ Verified | Commit 44a2102: `frontend-ci` run 36103873138 (API types match `docs/openapi.json`, ESLint with zero warnings, `tsc`, 37 Vitest tests, production build; simulator typecheck and tests) and `e2e` run 36103873129: three Playwright tests against the real stack (backend jar with the `demo` profile on PostGIS, Redis and Kafka, the Next.js production build, and the driver simulator). A new passenger registers; a passenger books a ride that a simulated driver accepts, drives and completes, rates it and opens the trip; an admin sees a completed ride in the ride list and the health components. **Manual E2E:** not possible on the development machine (no Docker), so the `e2e` workflow is the end-to-end evidence. `backend-ci` did not run on 44a2102 (frontend-only change); the backend it tested is unchanged since b4083ad, which passed all three workflows (`backend-ci` 36102888155, `frontend-ci` 36102888199, `e2e` 36102888249); the backend endpoints in 2f2bb63 passed `backend-ci` run 36099541397 |
| 9 Testing | ✅ Verified | Commit bf59c5b: `backend-ci` run 36110208581 (263 unit/web/ArchUnit tests and all 55 integration test methods, 0 skipped; JaCoCo over both: 91.0 % of lines and 68.2 % of branches overall, 91.4 % and 74.3 % in the service layer, above the new 88 %/70 % gate), `frontend-ci` run 36110208392 (75 Vitest tests; V8 coverage reported), `e2e` run 36110208393 (5 Playwright tests in 2.3 min against the whole stack). **End-to-end workflow:** `RideWorkflowIT` drives one ride from request to payment and AI insights over HTTP, a STOMP socket per participant and Kafka, first green in run 36105414302. **Open:** an intermittent stall, not reproduced since; see below |
| 10 Docker | ✅ Verified | Commit 26b06a3: `e2e` run 36121854154 does what the exit criterion says, from a clean checkout: `scripts/init-env.sh` writes `.env` with random secrets, `docker compose --profile demo up --build --wait` builds the three images and starts the stack, a login succeeds through the frontend container's `/api` proxy, and the 5 Playwright tests pass against the containers (2.1 min; stack healthy after 44 s). The first green run was 36118346579 (commit ebb1c43: images built in 74 s, stack healthy after 47 s). Images (uncompressed): backend 349 MB, frontend 217 MB, simulator 173 MB. `backend-ci` on the same commit passed as the pull-request run 36121859901 (all integration tests, including the refresh-token retry); its push twin 36121854152 hit the 20-minute limit in Build and test: the intermittent stall from Phase 9 again (see Found). **Not done:** `docker compose up` on this development machine, which has no Docker |
| 11 Observability | 🟡 Partly verified | Commit 704104d: `e2e` run 36129063738 meets the dashboard criterion. The compose stack (now with Prometheus and Grafana) was healthy after 57 s, the 5 Playwright tests passed with the simulator running, and `check_dashboards.py` then ran every panel query of the four provisioned dashboards through Grafana with no errors. Live values included 6 WebSocket sessions, 2.5 location reports/s, a 93 % cache hit ratio, 0.4 ms Redis commands, about 3 rides requested and 2 completed, and the backend up. Only the AI panels (AI disabled in e2e) and dead letters (none) were empty. `backend-ci` run 36129063755 passed every unit and integration test, including `RideWorkflowIT` reading the new counters from the Prometheus scrape and `ErrorReportingIT` (the admin test error reaches a stand-in Sentry with the id the API returned and without the caller's token). JaCoCo: 91.2 % of lines and 69.3 % of branches overall, 91.6 % and 74.8 % in the service layer. `frontend-ci` run 36128242166 (commit 9a62f52; the frontend has not changed since) passed with 83 Vitest tests. **Not yet done:** a test error seen in a real Sentry project, which needs a DSN from the owner. The browser path was checked by hand against a local stand-in (email and JWT arrived masked) |
| 12 Load testing | ✅ Verified | Commit 9605369: `load-test` run 36130629033 ran login, nearby search, ride creation and the full ride lifecycle at 10, 50 and 100 VUs (60 s each) against the compose stack's backend on a 4-vCPU runner (AMD EPYC 7763, 15 GB RAM) shared with k6. All 12 runs: 0 failed requests, 0 failed checks, every lifecycle offer delivered within 30 s. RPS, p50/p95/p99, error rates, the machine and the reading of the results are in [performance.md](performance.md#scenario-load-tests-phase-12) |

**Phase 2 delivered:** Spring Boot 4.1.1 / Java 21 skeleton; Flyway V1–V3; JWT access tokens and rotating refresh tokens with reuse detection; role-based security with JSON 401/403; `GlobalExceptionHandler`; request-id correlation; OpenAPI; auth, profile, driver onboarding, admin driver verification and user suspension; admin bootstrap; demo seed; docker-compose; `.env.example`; backend CI.

**Phase 3 delivered:** Flyway V4 (rides with PostGIS-generated geography, offers, status history, fare breakdowns, track points); ride state machine; fare calculator, live surge, signed fare quotes; OSRM routing with straight-line fallback; PostGIS matching rounds with widening radius, DB-enforced offer exclusivity, async trigger and restart-safe sweeper; accept/reject/en-route/arrive (geofenced)/start/complete (GPS-measured distance); passenger and driver cancellation with re-dispatch; driver online/offline/location; nearby cars (anonymised for passengers); trip history and timeline; suspension forces drivers offline.

**Design changes made in Phase 3** (recorded as D16 to D19 in the architecture doc): signed quotes instead of Redis quote storage; a partial unique index instead of a Redis offer lock; generated geography columns; ride-first lock ordering.

**Phase 4 delivered:**
- **Endpoint and security:** STOMP over native WebSocket at `/ws`. CONNECT is authenticated with the REST access token. Sockets are closed when that token expires, or when no CONNECT arrives within 10 s. A deny-by-default allow-list governs SUBSCRIBE and SEND. ERROR frames carry only a code.
- **Location stream:** drivers send GPS over STOMP, throttled per session, with errors answered on `/user/queue/errors`.
- **Pushes:** sent after commit to per-user queues: ride updates, the driver's location (to the passenger only), offers and offer withdrawals, presence changes, and the admin activity feed.
- **Tracking snapshot:** `GET /api/rides/{id}/tracking`, with staleness and a routed ETA.
- **Presence:** a sweeper takes silent AVAILABLE drivers offline and tells them; suspension also notifies an online driver.
- **Metrics:** WebSocket session gauge, dropped-location counter and push-failure counter.

**Design changes made in Phase 4:**
- **D20:** per-user queues with recipients computed at send time, replacing per-ride topics authorised at subscribe time.
- **D21:** a socket lives no longer than its access token.

**Bugs found by the Phase 4 tests:**
- **Silent rejections:** Spring's `setPreserveReceiveOrder` swallows interceptor exceptions, so rejected frames produced no ERROR frame. It is no longer used.
- **Timestamp precision:** nanosecond `Instant`s round up when stored as microseconds, which could cost a trip a second of duration. The application clock now ticks in microseconds.
- **Background job threads:** adding the broker's own scheduler and executors would have silently moved `@Scheduled` jobs onto the broker's thread pool and disabled the `@Async` executor. Both are now configured explicitly.

**Phase 5 delivered:**
- **Rate limiting:** a Redis Lua fixed-window limiter covering login, registration, fare estimates, booking, geocoding per user, and a global geocoder budget. It returns 429 with `Retry-After`, hashes subjects in keys, and fails open.
- **Caches:** road routes (15 min, routed answers only), surge per geohash-6 cell (60 s), geocoding (24 h), and a live ETA per ride (30 s, evicted on every status change), now carried in location pushes.
- **Geocoding:** Nominatim search and reverse endpoints, following its usage policy.
- **Redis as an optional dependency:** a short circuit breaker, readiness that ignores Redis, and cache and limiter metrics.
- **Benchmark:** a k6 cache benchmark in CI, with real upstream latency and real results.

**Design changes made in Phase 5:**
- **D22:** Redis is optional at runtime.
- **D23:** driver-location keys wait for the Kafka batch writer, instead of duplicating every write now.

**Phase 6 delivered:**
- **Outbox:** domain events are written to `outbox_events` in the transaction that caused them and relayed to Kafka by a relay thread that each commit wakes (with a 250 ms poll as a backstop). Rows are marked published only when the broker acknowledges them.
- **Topics:** 17 topics, one per ride status plus dispatch, offers, positions, presence, payments and notifications, each with a `.DLT`. All are declared by the application and can take an environment prefix.
- **Consumers:** matching (`ride.requested`, `ride.dispatch.requested`), payments (cash, and a labelled sandbox card with no real money; 20 % platform fee), notifications (stored, then pushed on `/user/queue/notifications`), batched position persistence, and a realtime bridge with one consumer group per instance that feeds the existing STOMP destinations.
- **Reliability:** `processed_events` idempotency in the handler's transaction; retries with backoff, then dead-lettering; undecodable records dead-lettered at once; metrics for outbox backlog, relay failures, dead letters and dropped positions.
- **Location hot path (D23):** positions and driver state live in Redis. A report touches Redis and Kafka only, and PostgreSQL gets one upsert per driver per batch.
- **API:** `POST /api/rides/{id}/rating`, the notifications list and read endpoints, and `payment` in the ride view.

**Design changes made in Phase 6** (D24 to D27): payloads are the domain records in a versioned envelope; one topic per ride status; a commit-woken relay thread; driver state written after commit with `SET NX` loads.

**Found while verifying Phase 6:** a malformed record on `ride.completed` is dead-lettered once per consumer group that reads the topic (payments and notifications), not once. The test first expected one copy; the DLT header `kafka_dlt-original-consumer-group` tells the copies apart.

**Carried forward:**
- **Phase 7:** `trip-analysis` consumer on `ride.completed` (AI), migration `V7__ai.sql`.
- **Phase 11:** Grafana panels for outbox backlog, consumer lag and dead letters.

**Phase 7 delivered:**
- **AIService:** Ollama (local), Anthropic (official Java SDK, structured outputs) and disabled providers behind one interface, selected by `AI_PROVIDER` (disabled by default).
- **Grounding:** `TripFactsAssembler` builds keyed facts from the database only, with no personal data; `TripObservationCalculator` adds deterministic observations that are shown whether or not a model is available.
- **Prompts:** versioned templates (`prompts/<name>/v1`); the version is stored with each result.
- **Validation:** schema, limits, fact keys and numeric grounding, with one corrective retry.
- **Resilience:** bulkhead (4), circuit breaker (50 % over 20 calls, open 60 s) and a retry loop; timeouts cover the whole call.
- **Pipeline:** a `trip-analysis` consumer on `ride.completed` in its own group; the model call runs outside any transaction.
- **API:** passenger-only analysis, regenerate (3/h) and questions (10/h); migration `V7__ai.sql`.

**Design changes made in Phase 7:** no Resilience4j `TimeLimiter` (each client enforces a whole-call deadline); retries are a small loop rather than Resilience4j `Retry`, to honour `Retry-After` once and never retry timeouts; SDK retries are off so the circuit breaker sees every failure.

**Found while verifying Phase 7** (details in [ai.md](ai.md) §5):
- **Timeout:** the JDK `HttpRequest.timeout` covers only the wait for headers; a real call ran 641 s past a 300 s limit. Fixed with a whole-call deadline, with regression tests for both providers.
- **Reasoning:** a 7B model blamed a fare increase on a multiplier that applied to both estimate and fare, with only true numbers. A clearer observation plus a prompt rule fixed it for codegemma, not for codeqwen.
- **Prompt injection:** codeqwen followed an injection (it told a joke) and validation cannot catch that; codegemma declined in every run. Documented as a limitation of small local models.
- **Latency:** 7B analyses took 66–159 s per call on a laptop GPU, so `AI_LOCAL_TIMEOUT=300s` is recommended for them.

**Carried forward:**
- **Phase 11:** Grafana panels for the AI metrics (`rideflow_ai_*`).
- **Phase 15:** a run against the external provider with a real key, recorded in ai.md, if a key is available.

**Phase 8 delivered:**
- **Backend for the screens:** driver earnings (gap-free hourly or daily buckets in a configurable reporting time zone), vehicle replacement while offline, and admin overview, ride-activity analytics, ride search and detail, system status (health components and live gauges) and audit-log search.
- **API contract:** `docs/openapi.json` is generated by `OpenApiContractTest` and the build fails when the code drifts from it. Record components are marked required and JSpecify `@Nullable` ones nullable, so the generated TypeScript types are exact. `frontend-ci` fails if the committed types are out of date.
- **Frontend:** Next.js 16 (App Router), React 19, Tailwind 4, TanStack Query, react-hook-form with zod rules mirroring the backend's, MapLibre maps. An original violet and coral design system with light and dark themes.
- **Auth:** access token in memory only; refresh through the HttpOnly cookie, single-flight per tab and serialised across tabs with a Web Lock (the backend treats reuse of a rotated refresh token as theft); proactive refresh before expiry and one retry after a 401.
- **Passenger:** booking with map or address search, quotes and payment choice; live tracking over STOMP with the driver's position and ETA; cancellation; rating; trip history and detail with fare breakdown, timeline and AI insights and questions.
- **Driver:** onboarding (profile and vehicle), console with offers, trip steps and GPS reporting, and an earnings chart.
- **Admin:** overview and analytics, ride search and detail, driver verification, user suspension, system status and audit log.
- **Realtime client:** reconnects with full-jitter backoff, renews the token before reconnecting, resubscribes and refetches snapshots; updates older than what is shown are ignored by version.
- **Simulator:** seeded demo drivers sign in, go online and drive real routes; `--trips N` books rides as demo passengers so the history holds real data.

**Design changes made in Phase 8:** the frontend calls the backend through a same-origin `/api` rewrite (the refresh cookie stays first-party and no CORS is needed), while the WebSocket connects directly; the simulator is TypeScript run directly by Node 24, with no build step.

**Found while verifying Phase 8:**
- **Two tabs signing each other out:** tabs refreshing at the same moment sent the same refresh token, and the second was treated as reuse. Fixed with the cross-tab lock.
- **A missed "ride ended" push:** the passenger fell back to the booking screen. The page now looks the ride up and shows how it ended.
- **Test timing:** the smoke test could click the map button while the pickup address was still resolving; it now waits for both pickers.
- **Windows checkouts:** prompt templates with CRLF line endings failed a test; templates are now normalised when loaded.

**Carried forward:**
- **Phase 9:** broader Playwright coverage (driver and admin flows, cancellation, reconnect) and frontend component tests.
- **Phase 10:** a Docker image for the frontend (Next standalone) and the simulator in the full compose file.
- **Phase 11:** Sentry for the frontend.

**Phase 9 delivered:**
- **Coverage:** JaCoCo instruments the unit and the integration tests separately and merges them into one report, summarised per package on the run page and uploaded as an artifact. The first baseline (run 36105414302) was 90.3 % of lines and 71.1 % of branches in the service layer; CI now fails below 88 % and 70 %. The gate is a Maven profile that CI turns on, because without Docker the integration tests skip and the figure would mean nothing. Frontend coverage (Vitest, V8) is reported without a gate: most of the frontend is pages, which Playwright covers and V8 does not see.
- **End-to-end workflow test:** `RideWorkflowIT`, the one planned in the architecture's testing strategy: the offer reaches the driver's socket, GPS streamed over the socket reaches the passenger and drives the arrival geofence, and payment, pushed notifications, the AI analysis, ratings, earnings and the admin timeline all agree on the same ride.
- **Gaps closed:** unit tests for profile and password changes, user suspension and reactivation, the cases where a ride must not be charged, and the `toString` redaction of credentials, tokens and personal details in request and response records.
- **Frontend tests:** the realtime provider's reconnect protocol (token on connect, resubscription, snapshot refetch, refresh after close 4001, jittered retry, stop when signed out), the active-ride hook including recovery from a missed final push, login (validation, errors, safe `next`), the rating form and the driver trip panel. Frontend tests went from 37 to 75.
- **Playwright:** a new driver registers, onboards, is verified by an admin in a second session, goes online from emulated GPS, accepts an offer, drives the ride step by step and finds it in their earnings; a passenger books from their own location, cancels while matching and sees it in their history. Both work 15 to 20 km out of town, beyond the simulator's drivers.
- **CI diagnostics:** Playwright failures, the backend's recent warnings and the simulator's log appear as annotations; a test fails after 5 minutes by default and names itself; every job has a 30-minute limit; a stalled build or backend start is captured as a condensed thread dump.

**Found while verifying Phase 9:**
- **Reconnect without jitter:** after a dropped socket, the first retry ran at once, because a successful connect reset the attempt counter to "first connection". Every client would come back at the same instant after a server restart, which the jittered backoff exists to prevent. Found by the new realtime test; the next connection now counts as the first retry.
- **Contract status codes:** `docs/openapi.json` listed 200 for all endpoints. Register, driver profile, booking and rating answer 201; the location report and AI regenerate 202; logout, offer rejection, notification reads and password change 204; the active ride and reverse geocoding 200 or 204. The handlers now declare their status and the contract and generated types follow (docs/api.md was already right).
- **Stale docs:** development.md named the integration test base class `PostgisContainerSupport`; it is `IntegrationTestContainers`.
- **Intermittent stall (open):** one `backend-ci` build (run 36106582214, commit b202b57) sat in "Build and test" for over 20 minutes with no timeout to end it, and one e2e backend start (run 36108659442) took 11 minutes, with Kafka consumers reporting poll timeouts until everything resumed at the same moment. Both commits' neighbours passed normally, and it has not recurred in the runs since. The cause is not known; the thread-dump capture above is there to catch it next time.

**Carried forward:**
- **Phase 11:** find the stall from a captured thread dump if it recurs; frontend Sentry.
- **Phase 12:** k6 load tests will exercise startup and consumers harder, which may reproduce the stall.

**Phase 10 delivered:**
- **Backend image:** built with the Maven wrapper in a JDK stage, split into Spring Boot's layers (dependencies, loader, snapshots, application) so a code change rebuilds only the last, small layer; Alpine JRE 21 at runtime, non-root, heap sized from the container's memory limit, the JVM as PID 1 (graceful SIGTERM), and a health check on actuator readiness.
- **Frontend image:** Next's standalone server on Node 24 Alpine, non-root. `next.config` and `NEXT_PUBLIC_` variables are fixed at build time, so `BACKEND_URL` (where the server forwards `/api`) and `NEXT_PUBLIC_WS_URL` (where the browser opens the socket) are build arguments with compose's values as defaults. Local `next start` keeps the regular output.
- **Simulator image:** Node 24 runs the TypeScript directly; only the runtime dependency is installed.
- **Compose:** PostGIS, Redis, Kafka, backend and frontend, started in health order; the simulator is behind the compose profile `demo`. Every secret comes from `.env` and compose refuses to start without the required ones; the actuator port is bound to localhost; `docker compose up -d postgres redis kafka` still gives host development just the infrastructure.
- **Clean-clone setup:** `scripts/init-env.sh` writes `.env` from `.env.example` with random secrets and never overwrites an existing one, so nobody picks or commits a password.
- **CI:** the `e2e` workflow now tests the compose stack itself (it replaced running the jar and `next start` on the runner). A failed image build, an unhealthy container or a failed test becomes annotations: build errors, container health, recent backend warnings, the simulator log, and a JVM thread dump taken with SIGQUIT (the JRE image has no `jcmd`).

**Design changes made in Phase 10:** the architecture's separate `docker.yml` became the `e2e` workflow, since running the Playwright suite against the containers is a stronger smoke test than a health probe; publishing images to a registry stays in Phase 13.

**Found while verifying Phase 10:**
- **Empty `public/`:** a clean clone has no `frontend/public` (it is empty, and git keeps no empty directories), so the image build failed copying it. The build now creates it.
- **IPv6 localhost:** in the Node Alpine image `localhost` resolves to `::1` first while Next listens on IPv4, so the frontend never became healthy. Health checks use `127.0.0.1`.
- **Signed out by a reload:** the driver test opened a page and reloaded it at once. The first load's refresh rotated the token, the reload aborted the request, and the browser kept the old cookie; presenting it counted as token theft and revoked the session. Any user reloading mid-refresh, or on a dropped connection, was signed out the same way. A rotated token presented again within `REFRESH_TOKEN_REUSE_GRACE` (10 s) while its successor has never been used now replaces that successor; once the successor has been used, it is theft as before. Rotation also locks the token row, so two refreshes with one token no longer both succeed.
- **Flaky end-to-end steps:** with the public OSRM server failing during one run, the booking form took longer than Playwright's default 5 s to offer a fare; steps that wait on public OSRM or Nominatim now allow 30 s. An admin assertion also matched a description as well as the stat label.
- **The stall again (open):** backend-ci run 36121854152 stalled in Build and test until its 20-minute limit, while the pull-request run of the same commit passed. The watchdog found no test JVM to dump at minute 12, so the stall was outside a test fork or the forks were not listed under their usual name; the watchdog now records every JVM and the running containers and dumps each JVM's main thread.
- **Rate limits and forwarded addresses (open):** the per-IP login and registration limits trust `X-Forwarded-For`, and the Next `/api` proxy appends to it rather than overwriting it, so a client can choose its own bucket. Raised as a separate task; see architecture section 9.

**Carried forward:**
- **Phase 11:** Prometheus and Grafana join the compose file.
- **Phase 13:** build caching for the images in CI, and publishing them to GHCR.
- **Phase 14:** frontend images built for the deployed backend's URL.

**Phase 11 delivered:**
- **Ride pipeline metrics:** `rideflow_rides_total{event}` (from `RideTransitionRecorder`, the one path every status change takes), `rideflow_matching_duration_seconds` (request to acceptance), `rideflow_offers_total{outcome}` and `rideflow_location_updates_total{result}`. Ride and offer counts are taken after the transaction commits, so a rolled-back accept is never counted. The latencies charted as percentiles publish histogram buckets.
- **Prometheus and Grafana in compose:** Prometheus scrapes the management port every 10 s. Grafana provisions the Service overview, Ride pipeline, Real-time & cache and AI dashboards, read-only in the UI. Both listen on 127.0.0.1 only; `init-env.sh` generates the Grafana admin password and anonymous access is off.
- **Sentry, backend and frontend:** off without a DSN. On the backend, ERROR log events become events (the exception handler answers every exception itself, so Sentry's resolver never sees one). On the frontend: uncaught errors, both error boundaries and server request errors. Tracing and replay stay off.
- **Scrubbing:** on top of `sendDefaultPii=false`. Requests keep method, path and a few harmless headers; the user keeps only its id; emails, JWTs and bearer tokens are masked in messages, exceptions and breadcrumbs, nested data included.
- **Admin > System:** shows whether reporting is on, and sends a test error from the backend (`POST /api/admin/system/test-error`, answering with the event id) or from the browser.
- **CI:** after its rides, the `e2e` workflow runs every panel query through Grafana. A query error fails the run, and so does an empty panel the rides must fill.

**Design changes made in Phase 11:**
- Prometheus and Grafana run with the default compose services instead of an `observability` profile, so the dashboards are there whenever the stack is.
- The driver services cancel pending offers through `DriverOfferWithdrawal`, which also counts them, instead of calling the repository directly.
- Sentry's Next.js build wrapper (`withSentryConfig`) is not used: it exists mainly to upload source maps, which needs a Sentry org, project and auth token, so browser stack traces arrive minified for now.

**Found while verifying Phase 11:**
- **Console arguments reached Sentry unmasked:** in the local browser check, console breadcrumbs keep their arguments as an array, which the first scrubber skipped. Both scrubbers now mask strings however deeply they are nested.
- **Redis latency metric renamed in Spring Boot 4:** the panel queried Lettuce's old command-latency series, which Boot 4 no longer produces, so it was empty in e2e run 36128242176. Boot 4 times commands through Lettuce observations (`lettuce_seconds`, by `db_operation`). The panel now reads those, the dashboard check requires them, and `RideWorkflowIT` asserts them.
- **A test that insisted on 200:** `ReportingIT` read the expected 409 with a helper that asserts 200 first (backend-ci run 36128242162); the endpoint itself answered correctly.
- **The stall:** did not recur in this phase's `backend-ci` runs (about 3.5 minutes each); still open.

**Carried forward:**
- **Owner:** set `SENTRY_DSN` and `NEXT_PUBLIC_SENTRY_DSN`, then send both test errors from Admin > System, to close the Sentry criterion.
- **Phase 13:** source-map upload to Sentry from CI, if a Sentry auth token is available.

**Phase 12 delivered:**
- **Scenarios** (`load-tests/scenarios/`): login; nearby search with 30 drivers reporting positions in the background; ride creation (estimate, book, cancel); the full lifecycle, where each VU is a passenger and a driver on its own grid cell, from booking through matching over Kafka to completion. Each runs at 10, 50 and 100 VUs.
- **Honest numbers:** only the measured scenario is counted (not account setup or background traffic). The report records the runner. The three settings that differ from production are explained in `load-tests/README.md`.
- **CI:** the `load-test` workflow starts the compose stack's backend with the `prod` profile, runs every scenario and level, and puts the report on the run page and in an artifact. It runs on changes to the scenarios or on demand, not on every commit.

**Found by Phase 12** (details in performance.md):
- **Login:** 8–10 logins/s on the shared 4-vCPU runner, whatever the load: the price of BCrypt at cost 12. Kept; capacity comes from instances, and the rate limits stop floods first.
- **Bookings slow down, estimates do not:** the write transactions (book, cancel) went from about 35 ms to over 300 ms p95 between 10 and 100 VUs, while estimates stayed at 21 ms. The pool of 10 connections is the likely cause, not yet confirmed.
- **Matching runs on one thread:** the `matching` listener sets no concurrency, so each instance runs matching rounds one at a time although `ride.requested` has 3 partitions. At 100 VUs a driver saw the offer 1.8 s after booking (262 ms at 10 VUs), and fewer rides completed than at 50 VUs.
- **Dry run first:** a local dry run of the scripts against a stand-in API found two script mistakes before they cost a CI run. Its numbers are not recorded.

**Carried forward:**
- **Next:** listener concurrency for `matching` equal to the partition count, re-measured with the lifecycle scenario.
- **Next:** record the backend's connection-pool waits and consumer lag during each load run (from `/actuator/prometheus`), to confirm or rule out the causes above.

## Phases

| Phase | Scope | Exit criteria (verified) |
|---|---|---|
| **1 Architecture** | This document set | Reviewed and approved by owner |
| **2 Backend foundation** | Spring Boot skeleton, config properties, Flyway V1–V3, users/drivers/vehicles entities and repos, auth (register/login/refresh/logout, JWT, BCrypt), `GlobalExceptionHandler`, OpenAPI, ArchUnit rules, dev `docker-compose` with PostGIS + Redis + Kafka | `./mvnw verify` green; auth unit + `@WebMvcTest` + Testcontainers tests pass; Swagger UI shows auth endpoints |
| **3 Ride system** | Flyway V4–V5, `FareCalculator`, routing providers, quotes, `RideStateMachine`, ride create/cancel/accept/reject/en-route/arrive/start/complete, driver online/offline, PostGIS nearby query, matching + offers + sweeper. Events go through a `DomainEventPublisher` port whose Phase 3 adapter is in-process (after-commit) | State-machine exhaustive tests; PostGIS query tests with known geometry; concurrent-accept test; `EXPLAIN ANALYZE` captured |
| **4 Real-time** | STOMP config, auth and subscription interceptors, location ingestion, tracking snapshot, passenger pushes, offer pushes, presence sweeper | STOMP client integration test: driver location reaches passenger; unauthorised subscribe rejected; stale handling tested |
| **5 Redis** | Location/active-ride keys, route/geocode/surge caches, throttled live ETA in location pushes, Lua rate limiter (quotes, offer exclusivity and WebSocket authorisation no longer need Redis: D16, D17, D20) | TTL/invalidation tests; k6 before/after numbers for estimate and geocode recorded |
| **6 Kafka** | Replace in-process adapter with outbox + relay; topics; consumers (matching, notifications, payments, location-persistence batch, realtime bridge); DLT; `processed_events` | Integration test: full lifecycle flows through real Kafka (Testcontainers); DLT test; redelivery idempotency test |
| **7 AI** | `AIService`, Local (Ollama) + External providers, prompt registry, `TripFactsAssembler`, deterministic observations, validator, Resilience4j, Q&A endpoint | WireMock failure-matrix tests; ride completion unaffected when AI is down; real run against a local model documented |
| **8 Frontend** | Design system, auth, passenger flow, live tracking, trip history, AI insights, driver console, onboarding, earnings, admin console | Lint + typecheck + Vitest; manual E2E with simulator; Playwright smoke |
| **9 Testing** | Close coverage gaps, end-to-end workflow test, frontend tests | JaCoCo report; all suites green in CI |
| **10 Docker** | Multi-stage Dockerfiles (backend: layered jar on JRE 21; frontend: Next standalone), full compose with healthchecks, `demo` profile | `docker compose up` from clean clone → working app |
| **11 Observability** | Custom metrics, Prometheus scrape, 4 provisioned Grafana dashboards, Sentry backend + frontend with scrubbing | Dashboards show live data during a simulator run; test error visible in Sentry |
| **12 Load testing** | k6: login, ride creation, nearby search, full lifecycle, concurrency at 10/50/100 VUs | Real results (RPS, p95, p99, error rate) + machine spec recorded in docs |
| **13 CI/CD** | Three workflows + gitleaks | Green runs on GitHub |
| **14 Deployment** | Vercel frontend; backend container; managed PostGIS/Redis/Kafka | Public URL if free resources allow; `docs/deployment.md` |
| **15 Documentation** | README (all 21 sections), development.md, deployment.md, screenshots from real runs | Definition-of-done checklist ticked with evidence |

## Decisions that need owner confirmation (defaults in bold)

1. **Demo city / currency:** **Hyderabad, INR** (configurable via env).
2. **External AI provider for `ExternalAIService`:** **Anthropic Messages API** (model via env), with local **Ollama** for development.
3. **Payments:** **CASH + clearly labelled sandbox card gateway** (no real money movement).
4. **Spring Boot line:** **latest stable 4.x** (3.5 left OSS support in mid-2026). Exact versions pinned in Phase 2 after verifying compatibility with springdoc, Sentry, Testcontainers and Hibernate Spatial.
