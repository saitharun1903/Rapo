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
├── docker-compose.yml       profiles: default (infra + apps), demo (simulator), observability
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
