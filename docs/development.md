# RideFlow — Development Guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Maven itself is not needed: use the wrapper `backend/mvnw` |
| Docker Desktop | recent, Compose v2 | Runs the whole stack, or just PostGIS, Redis and Kafka, and the Testcontainers integration tests |
| Node.js | 24+ | Frontend and driver simulator (the simulator runs TypeScript directly on Node 24) |

> **Windows / OneDrive:** keep the repository outside OneDrive-synced folders. OneDrive locks files in
> `backend/target` and `node_modules`, which makes `mvnw clean` fail intermittently.

## First run

`.env` holds every setting and secret, and is git-ignored. Create it once:

```bash
./scripts/init-env.sh             # .env from .env.example, with random values for every required secret
```

(or `cp .env.example .env` and fill in each REQUIRED value by hand). Then either run everything in Docker, or
run the infrastructure in Docker and the apps on the host while you work on them.

### Everything in Docker

```bash
docker compose --profile demo up --build   # http://localhost:3000; the simulator's drivers are online
docker compose up --build                  # the same without the simulator
docker compose --profile demo down         # stop (add -v to delete the database and Kafka volumes)
```

Compose starts PostGIS, Redis and Kafka, then the backend once they are healthy, then the frontend (and the
simulator) once the backend is ready. The backend image is a layered Spring Boot jar on an Alpine JRE 21, the
frontend image is Next's standalone server on Node 24, and both run as non-root users. The frontend forwards
`/api` to the backend container; the browser opens the WebSocket on the backend's published port
(`BACKEND_PORT`). Both are fixed when the frontend image is built (Next inlines them), so changing
`BACKEND_PORT` needs `--build`.

### Monitoring

The compose stack includes Prometheus and Grafana, both reachable from this machine only:

- Grafana: http://localhost:3001, user `admin`, password `GRAFANA_ADMIN_PASSWORD` from `.env`. The RideFlow
  folder holds four dashboards (service overview, ride pipeline, real-time and cache, AI). They are provisioned
  from `infrastructure/grafana/dashboards` and read-only in the UI: change the JSON and restart Grafana.
- Prometheus: http://localhost:9090. It scrapes the backend's `/actuator/prometheus` every 10 seconds and keeps
  `PROMETHEUS_RETENTION` (7 days) of data.

With `--profile demo` the simulator keeps drivers online and the passengers' rides flowing, so every ride and
real-time panel shows live data within a minute; the AI dashboard stays empty unless `AI_PROVIDER` is set.
An `.env` created before Phase 11 lacks `GRAFANA_ADMIN_PASSWORD`, and compose refuses to start until it is
added (any strong value).

### Error reporting (Sentry)

Off by default. To turn it on, create a backend and a frontend project in Sentry and set `SENTRY_DSN` (backend,
read at startup) and `NEXT_PUBLIC_SENTRY_DSN` (frontend, inlined at build time, so rebuild the frontend image
after changing it) in `.env`. Personal data and credentials are removed before anything is sent
([architecture.md](architecture.md) §15). To check delivery, sign in as the admin, open **System**, and use
**Send backend test error** or **Send browser test error**: each shows the Sentry event id to search for.

### Apps on the host

```bash
docker compose up -d postgres redis kafka  # infrastructure only
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

- API: http://localhost:8080 · Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator (management port): http://localhost:8081/actuator/health
- The backend reads `../.env` automatically (see `spring.config.import` in `application.yml`).
- With the `demo` profile, seed accounts are created (all use `DEMO_USER_PASSWORD`):
  `admin@rideflow.example.com`, `ananya@` / `rahul@` / `meera@rideflow.example.com` (passengers),
  `driver.arjun@`, `driver.farhan@`, `driver.lakshmi@`, `driver.vikram@`, `driver.sneha@rideflow.example.com`
  (verified drivers) and `driver.karthik@rideflow.example.com` (pending verification).

### Frontend

```bash
cd frontend
npm install
npm run dev                       # http://localhost:3000
```

The browser calls `/api/*` on the frontend's own origin and Next forwards it to `BACKEND_URL`
(default `http://localhost:8080`), so the refresh cookie stays first-party. The WebSocket goes straight to
`NEXT_PUBLIC_WS_URL` (default `ws://localhost:8080/ws`); the backend's `CORS_ALLOWED_ORIGINS` must include
the frontend's origin. Other optional variables: `NEXT_PUBLIC_MAP_STYLE_URL` (default OpenFreeMap, no key),
`NEXT_PUBLIC_MAP_CENTER_LAT` / `_LNG`.

### Driver simulator (demo only)

```bash
cd simulator
npm install
DEMO_USER_PASSWORD=... npm start              # seeded drivers go online and serve rides booked in the web app
DEMO_USER_PASSWORD=... node src/index.ts --trips 5   # seeded passengers also book 5 rides, then it exits
```

It signs in as the seeded drivers through the public API, streams their positions over the same STOMP
destination as the web app, accepts offers and drives the routed path. Speed, report interval and start
area are `SIM_*` variables (see `simulator/src/config.ts`). A driver in the web app can also place
themself on the map when the browser has no GPS.

## Tests

```bash
cd backend
./mvnw test      # unit, web-slice (MockMvc + real JWT security) and ArchUnit tests; no Docker needed
./mvnw verify    # additionally runs *IT integration tests against real PostGIS, Redis and Kafka containers
```

Integration tests extend `IntegrationTestContainers`. They are **skipped** (not failed) when Docker is
unavailable, so `verify` passing locally without Docker does not mean they ran: check the
`Skipped:` count in the output. CI always runs them, and fails if any was skipped.

**Coverage.** `verify` writes a JaCoCo report of the unit and integration tests together to
`target/site/jacoco/index.html`. In CI a floor on the service layer (`com.rideflow.service`, 88 % of lines
and 70 % of branches) fails the build; the per-package figures are on the run page. The floor needs the
integration tests, so it is off locally unless asked for: `./mvnw verify -Pcoverage-gate` (with Docker).

| Suite | What it proves |
|---|---|
| `ArchitectureTest` | Layering rules: controllers never touch repositories/entities, no transactional controllers, etc. |
| `*WebTest` | HTTP contract: status codes, `ApiError` shape, validation, role rules, security headers, cookies |
| `*Test` (service/entity) | Business rules: refresh rotation and reuse detection, driver verification transitions, normalisation |
| `SchemaMigrationIT` | Flyway migrations apply, Hibernate mappings validate, DB constraints enforce invariants |
| `AuthAndOnboardingFlowIT` | End-to-end: register → login → refresh rotation → reuse detection; driver onboarding → admin verification; suspension |
| `DemoSeedIT` | Seed loads and pgcrypto-hashed demo passwords work with the application's password encoder |
| `ReportingIT` | Earnings, admin overview, analytics series, ride search and detail against a real completed and paid ride; vehicle replacement rules and its audit entry |
| `RideWorkflowIT` | One ride from request to payment and AI insights the way the apps drive it: HTTP commands, a STOMP socket per participant, Kafka between every step; then ratings, earnings and the admin view agree |
| `OpenApiContractTest` | `docs/openapi.json` matches the code (see below) |

### API contract

`docs/openapi.json` is generated from the controllers and DTOs. After changing an endpoint or a DTO:

```bash
cd backend && ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true
cd ../frontend && npm run api:types     # regenerates src/lib/api/schema.d.ts
```

A record component that can be `null` must be annotated with JSpecify `@Nullable`; everything else is
marked required in the schema. The backend build fails when the file is stale, and the frontend build
fails when the generated types are.

### Frontend and simulator

```bash
cd frontend
npm run lint && npm run typecheck && npm test && npm run build
npm run test:coverage                   # the same tests with a V8 coverage report in coverage/
cd ../simulator && npm run typecheck && npm test
```

Vitest and Testing Library cover the logic that is easy to get wrong without a browser: token refresh and
retry, the realtime reconnect protocol, the active-ride hook, forms and their server errors, and the driver's
trip controls. Pages as a whole are covered by Playwright.

### End-to-end (Playwright)

`frontend/e2e` holds tests that need the whole stack: infrastructure, the backend with the `demo` profile and
the simulator. The `e2e` workflow runs them against the Docker Compose stack from a clean checkout:
`init-env.sh`, `docker compose --profile demo up --build --wait`, a login through the frontend container, then
Playwright. Afterwards `.github/scripts/check_dashboards.py` runs every Grafana panel query against the
Prometheus that watched those rides: a query error fails the run, and so does an empty panel that the rides
must have filled.

| Spec | Journey |
|---|---|
| `smoke.spec.ts` | A passenger registers; a passenger books a ride that a simulated driver completes, rates it and opens the trip; an admin sees rides and system state |
| `driver.spec.ts` | A new driver registers and onboards, an admin verifies them in a second session, they go online from emulated GPS, accept an offer, drive the ride step by step and find it in their earnings |
| `passenger.spec.ts` | A passenger books from their own location, cancels while matching, and sees the ride in their history |

The driver and cancellation tests work 15 to 20 km out of the city centre, beyond the simulator's drivers'
reach, so those drivers never take their rides. Locally, against the compose stack started with
`SIM_SPEED_MPS=60` and `SIM_BOARDING_MS=2000` in `.env` (so a whole ride fits in a test's time limit):

```bash
cd frontend
npx playwright install chromium
PLAYWRIGHT_BASE_URL=http://localhost:3000 DEMO_USER_PASSWORD=... npm run e2e
```

Without `PLAYWRIGHT_BASE_URL`, Playwright starts the frontend itself (`npm run build` first) against a
backend and simulator you run on the host.

## Continuous integration

| Workflow | Runs on | Checks |
|---|---|---|
| `backend-ci` | changes to `backend/` or the API contract | Build, unit, web and ArchUnit tests, integration tests on Testcontainers, merged coverage with a gate on the service layer |
| `frontend-ci` | changes to `frontend/`, `simulator/` or the API contract | Generated API types match `docs/openapi.json`, ESLint, `tsc`, Vitest with coverage, production build; simulator typecheck and tests |
| `e2e` | changes to any app, the compose file or `infrastructure/` | The compose stack from a clean checkout, the Playwright suite, every Grafana panel; on `main`, publishes the tested images to GHCR and deploys the backend image ([deployment.md](deployment.md#updates-and-rollback)) |
| `secret-scan` | every push and pull request | gitleaks over the whole history |
| `load-test` | changes to `load-tests/scenarios/`, or by hand | k6 scenarios at 10/50/100 VUs ([performance.md](performance.md)) |
| `cache-benchmark` | changes to `load-tests/cache-benchmark/`, or by hand | Cache on/off comparison |
| `free-tier-fit` | changes to `render.yaml`, `infrastructure/free-tier/` or the backend Dockerfile, or by hand | The backend at 512 MB and 0.1 CPU with the Blueprint's settings: startup time, peak memory, login and whole rides ([deployment.md](deployment.md#measured)) |

Dependabot opens weekly update pull requests (`.github/dependabot.yml`), which go through the same checks.
`python scripts/ci_status.py [commit]` shows every run for a commit once, without waiting; a workflow with
no run was not triggered by that change.

**Secret scanning locally:** with [gitleaks](https://github.com/gitleaks/gitleaks) installed,
`gitleaks git --redact .` scans the history the way CI does. A finding that is not a secret (a test
fixture, say) is accepted by adding its fingerprint, printed by the scan and by the CI annotation, to
`.gitleaksignore` with a comment saying why.

**Published images:** every commit on `main` that passes `e2e` is pushed as
`ghcr.io/saitharun1903/rideflow-{backend,frontend,simulator}` with the commit SHA and `latest`. The backend
and simulator images take all settings from the environment. The frontend image is built for the compose
stack's URLs, so a deployment builds its own:
`docker build --build-arg BACKEND_URL=... --build-arg NEXT_PUBLIC_WS_URL=... frontend`. The public
deployment builds the frontend on Vercel instead ([deployment.md](deployment.md)).

## Environment variables

Defined in `.env.example`. Variables without a default are required; the application fails fast at
startup if one is missing.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | no | — | `demo` (seed data) or `prod` (JSON logs, API docs off). Containers only; with Maven use `-Dspring-boot.run.profiles` |
| `POSTGRES_DB` / `POSTGRES_USER` | no | `rideflow` | Database created by the compose PostGIS container |
| `POSTGRES_PASSWORD` | **yes** (compose) | — | Password for the compose PostGIS container |
| `POSTGRES_PORT` | no | `5432` | Host port for PostGIS |
| `DATABASE_URL` | no | `jdbc:postgresql://localhost:5432/rideflow` | Backend JDBC URL |
| `DATABASE_USERNAME` | no | `rideflow` | Backend DB user |
| `DATABASE_PASSWORD` | **yes** | — | Backend DB password |
| `DATABASE_POOL_SIZE` | no | `10` | Hikari maximum pool size |
| `REDIS_PASSWORD` | **yes** (compose) | — | Redis `requirepass`; the backend authenticates with the same value |
| `REDIS_HOST` | no | `localhost` | Redis host for the backend |
| `REDIS_PORT` | no | `6379` | Redis port (host port in compose, connection port for the backend) |
| `REDIS_SSL_ENABLED` | no | `false` | TLS to Redis (managed Redis services) |
| `CACHE_ENABLED` | no | `true` | `false` bypasses every Redis cache (benchmark baseline, debugging) |
| `RATE_LIMIT_ENABLED` | no | `true` | `false` disables rate limiting (benchmarks; the integration tests enable it only in `RateLimitIT`) |
| `ROUTING_PROVIDER` | no | `osrm` | `osrm` or `straight-line` (no network) |
| `ROUTING_BASE_URL` | no | `https://router.project-osrm.org` | OSRM server; self-host it for anything beyond light development |
| `GEOCODING_PROVIDER` | no | `nominatim` | `nominatim` or `disabled` (search endpoints then return 503) |
| `GEOCODING_BASE_URL` | no | `https://nominatim.openstreetmap.org` | Nominatim server |
| `GEOCODING_USER_AGENT` | no | `RideFlow/0.1 (+repo URL)` | Identifying User-Agent, required by the Nominatim usage policy; add a contact address when deploying |
| `GEOCODING_COUNTRY_CODES` | no | `in` | Countries search results are limited to |
| `KAFKA_HOST_PORT` | no | `29092` | Host port of the Kafka EXTERNAL listener |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:29092` | Kafka brokers for the backend |
| `KAFKA_TOPIC_PREFIX` | no | (empty) | Prepended to every topic and consumer group, to share one cluster between environments |
| `KAFKA_REPLICATION_FACTOR` | no | `1` | Replication of the declared topics; at least 3 on a real cluster |
| `KAFKA_PARTITIONS` | no | `3` | Partitions of every topic and its dead-letter topic (managed services bill per partition) |
| `KAFKA_SECURITY_PROTOCOL` | no | `PLAINTEXT` | `SASL_SSL` for a managed broker ([deployment.md](deployment.md)) |
| `KAFKA_SASL_MECHANISM` | no | `SCRAM-SHA-256` | SASL mechanism, used only with a SASL protocol |
| `KAFKA_USERNAME` / `KAFKA_PASSWORD` | no | (empty) | SCRAM credentials of a managed broker; letters and digits only in the password |
| `REPORTING_TIME_ZONE` | no | `Asia/Kolkata` | Zone in which earnings and admin analytics cut hour and day buckets |
| `AI_PROVIDER` | no | `disabled` | `local` (Ollama), `external` (Anthropic API) or `disabled`; trips still get their computed observations when disabled |
| `AI_LOCAL_BASE_URL` | no | `http://localhost:11434` | Ollama server |
| `AI_LOCAL_MODEL` | no | `llama3.2` | A model already pulled into Ollama (`ollama pull <model>`) |
| `AI_LOCAL_TIMEOUT` | no | `120s` | Whole call, response included. 7B models took 66–159 s per analysis on a laptop GPU; use `300s` for them ([ai.md](ai.md) §5) |
| `ANTHROPIC_API_KEY` | with `external` | — | Anthropic API key; startup fails if `AI_PROVIDER=external` and it is missing. Never commit it |
| `AI_EXTERNAL_MODEL` | no | `claude-opus-5` | Claude model id |
| `AI_EXTERNAL_BASE_URL` | no | `https://api.anthropic.com` | |
| `JWT_SECRET` | **yes** | — | HS256 signing key, ≥ 32 bytes (`openssl rand -base64 48`) |
| `JWT_ISSUER` | no | `rideflow` | `iss` claim, validated on every request |
| `JWT_ACCESS_TOKEN_TTL` | no | `15m` | Access-token lifetime |
| `REFRESH_TOKEN_TTL` | no | `14d` | Refresh-token lifetime (sliding via rotation) |
| `REFRESH_TOKEN_REUSE_GRACE` | no | `10s` | How soon a rotated refresh token may come back (while its successor is unused) without counting as theft: a reload or dropped connection mid-refresh |
| `REFRESH_COOKIE_SECURE` | no | `true` (`false` in compose) | `Secure` flag on the refresh cookie; `false` only for http://localhost |
| `REFRESH_COOKIE_SAME_SITE` | no | `Lax` | `None` (with Secure) if the frontend is on a different site than the API |
| `BCRYPT_STRENGTH` | no | `12` | BCrypt cost factor |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:3000` | Comma-separated browser origins allowed to call the API |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | no | — | Creates the first admin at startup if none exists |
| `DEMO_USER_PASSWORD` | **yes** with `demo` | — | Password for all seed accounts (hashed inside PostgreSQL; must not contain `'`) |
| `SERVER_PORT` | no | `8080` | API port |
| `MANAGEMENT_PORT` | no | `8081` | Actuator port; keep it off the public internet |
| `API_DOCS_ENABLED` | no | `true` (`false` in `prod`) | Swagger UI and `/v3/api-docs` |
| `FRONTEND_PORT` / `BACKEND_PORT` | no | `3000` / `8080` | Host ports of the compose frontend and backend. A different frontend port needs `CORS_ALLOWED_ORIGINS` to match |
| `DOCKER_AI_LOCAL_BASE_URL` | no | `http://host.docker.internal:11434` | Where the backend container reaches Ollama on the host (`AI_LOCAL_BASE_URL` is for a backend on the host) |
| `SIM_SPEED_MPS` / `SIM_BOARDING_MS` | no | `11` / `5000` | Simulated drivers' speed and boarding wait, compose `demo` profile |
| `GRAFANA_ADMIN_PASSWORD` | **yes** (compose) | — | Password of Grafana's `admin` user |
| `GRAFANA_PORT` / `PROMETHEUS_PORT` | no | `3001` / `9090` | Host ports of Grafana and Prometheus, bound to 127.0.0.1 |
| `PROMETHEUS_RETENTION` | no | `7d` | How long Prometheus keeps samples |
| `SENTRY_DSN` | no | (empty: off) | Backend error reporting to Sentry |
| `NEXT_PUBLIC_SENTRY_DSN` | no | (empty: off) | Frontend error reporting; fixed when the frontend is built |
| `SENTRY_ENVIRONMENT` | no | `local` | Environment name on Sentry events (backend and frontend) |

## Conventions

- Controllers are thin: validate the DTO, read the principal (`@AuthenticationPrincipal AuthenticatedUser`), call one service method.
- Services own transactions. Entities expose intention-revealing methods (`driver.verify(...)`) instead of setters.
- Errors are `RideFlowException` subclasses with an `ErrorCode`; never return `null` or swallow exceptions.
- Every schema change is a new Flyway migration; never edit an applied one.
- Seed/demo data lives only in `db/seed` and is loaded only by the `demo` profile.
