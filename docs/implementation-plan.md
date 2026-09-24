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

**Phase 2 delivered:** Spring Boot 4.1.1 / Java 21 skeleton; Flyway V1–V3; JWT access tokens and rotating refresh tokens with reuse detection; role-based security with JSON 401/403; `GlobalExceptionHandler`; request-id correlation; OpenAPI; auth, profile, driver onboarding, admin driver verification and user suspension; admin bootstrap; demo seed; docker-compose; `.env.example`; backend CI.

**Phase 3 delivered:** Flyway V4 (rides with PostGIS-generated geography, offers, status history, fare breakdowns, track points); ride state machine; fare calculator, live surge, signed fare quotes; OSRM routing with straight-line fallback; PostGIS matching rounds with widening radius, DB-enforced offer exclusivity, async trigger and restart-safe sweeper; accept/reject/en-route/arrive (geofenced)/start/complete (GPS-measured distance); passenger and driver cancellation with re-dispatch; driver online/offline/location; nearby cars (anonymised for passengers); trip history and timeline; suspension forces drivers offline.

**Design changes made in Phase 3** (recorded as D16 to D19 in the architecture doc): signed quotes instead of Redis quote storage; a partial unique index instead of a Redis offer lock; generated geography columns; ride-first lock ordering.

**Carried forward:** Phase 4 replaces REST location ingestion with WebSocket and adds the presence sweeper (drivers with stale GPS go offline); Phase 5 adds login and estimate rate limiting and caching of surge, routes and geocoding; Phase 6 swaps the in-process event adapter for the outbox and Kafka, and adds payments and ratings.

## Phases

| Phase | Scope | Exit criteria (verified) |
|---|---|---|
| **1 Architecture** | This document set | Reviewed and approved by owner |
| **2 Backend foundation** | Spring Boot skeleton, config properties, Flyway V1–V3, users/drivers/vehicles entities and repos, auth (register/login/refresh/logout, JWT, BCrypt), `GlobalExceptionHandler`, OpenAPI, ArchUnit rules, dev `docker-compose` with PostGIS + Redis + Kafka | `./mvnw verify` green; auth unit + `@WebMvcTest` + Testcontainers tests pass; Swagger UI shows auth endpoints |
| **3 Ride system** | Flyway V4–V5, `FareCalculator`, routing providers, quotes, `RideStateMachine`, ride create/cancel/accept/reject/en-route/arrive/start/complete, driver online/offline, PostGIS nearby query, matching + offers + sweeper. Events go through a `DomainEventPublisher` port whose Phase 3 adapter is in-process (after-commit) | State-machine exhaustive tests; PostGIS query tests with known geometry; concurrent-accept test; `EXPLAIN ANALYZE` captured |
| **4 Real-time** | STOMP config, auth and subscription interceptors, location ingestion, tracking snapshot, passenger pushes, offer pushes, presence sweeper | STOMP client integration test: driver location reaches passenger; unauthorised subscribe rejected; stale handling tested |
| **5 Redis** | Location/active-ride/participants keys, route/geocode/surge caches, Lua rate limiter (quotes and offer exclusivity no longer need Redis: D16, D17) | TTL/invalidation tests; k6 before/after numbers for estimate and geocode recorded |
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
