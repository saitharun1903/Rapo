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
| 2 Backend foundation | 🟡 Code complete, integration tests not yet executed | `./mvnw verify`: 82 unit/web/ArchUnit tests pass; 13 Testcontainers integration tests **skipped: no Docker on the dev machine**. Flyway SQL and Hibernate `validate` have therefore not yet run against a real PostGIS. Must be run once Docker is available, before Phase 3 builds on the schema |

Phase 2 delivered: Spring Boot 4.1.1 / Java 21 skeleton; Flyway V1–V3 (users, refresh_tokens, audit_logs, drivers, vehicles, driver_locations with a GiST index); JWT access tokens + rotating refresh tokens with reuse detection; BCrypt; role-based security with JSON 401/403; `GlobalExceptionHandler` + `ApiError`; request-id correlation; OpenAPI; auth, profile, driver onboarding, admin driver verification and admin user suspension endpoints; admin bootstrap from env; demo seed via Flyway placeholder; docker-compose (PostGIS, Redis, Kafka KRaft); `.env.example`; `docs/development.md`.

Carried into Phase 3: suspending a DRIVER account must also force the driver offline (and be refused while they are on a trip); login rate limiting arrives with Redis in Phase 5.

## Phases

| Phase | Scope | Exit criteria (verified) |
|---|---|---|
| **1 Architecture** | This document set | Reviewed and approved by owner |
| **2 Backend foundation** | Spring Boot skeleton, config properties, Flyway V1–V3, users/drivers/vehicles entities and repos, auth (register/login/refresh/logout, JWT, BCrypt), `GlobalExceptionHandler`, OpenAPI, ArchUnit rules, dev `docker-compose` with PostGIS + Redis + Kafka | `./mvnw verify` green; auth unit + `@WebMvcTest` + Testcontainers tests pass; Swagger UI shows auth endpoints |
| **3 Ride system** | Flyway V4–V5, `FareCalculator`, routing providers, quotes, `RideStateMachine`, ride create/cancel/accept/reject/en-route/arrive/start/complete, driver online/offline, PostGIS nearby query, matching + offers + sweeper. Events go through a `DomainEventPublisher` port whose Phase 3 adapter is in-process (after-commit) | State-machine exhaustive tests; PostGIS query tests with known geometry; concurrent-accept test; `EXPLAIN ANALYZE` captured |
| **4 Real-time** | STOMP config, auth and subscription interceptors, location ingestion, tracking snapshot, passenger pushes, offer pushes, presence sweeper | STOMP client integration test: driver location reaches passenger; unauthorised subscribe rejected; stale handling tested |
| **5 Redis** | Location/active-ride/offer-lock/participants keys, quote store, route/geocode/surge caches, Lua rate limiter | TTL/invalidation tests; k6 before/after numbers for estimate and geocode recorded |
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
