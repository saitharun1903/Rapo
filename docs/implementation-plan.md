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

**Carried forward:**
- **Phase 6:** Redis location and active-ride keys, together with the batched PostgreSQL writer.
- **Phase 6:** swaps the in-process event adapter for the outbox and Kafka (the realtime bridge feeds the same destinations), and adds payments, ratings and `/user/queue/notifications`.

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
