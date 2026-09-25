# RideFlow — Development Guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Maven itself is not needed: use the wrapper `backend/mvnw` |
| Docker Desktop | recent | Runs PostGIS, Redis and Kafka, and the Testcontainers integration tests |
| Node.js | 22+ | Frontend (Phase 8) |

> **Windows / OneDrive:** keep the repository outside OneDrive-synced folders. OneDrive locks files in
> `backend/target` and `node_modules`, which makes `mvnw clean` fail intermittently.

## First run

```bash
cp .env.example .env              # then fill in every REQUIRED value
docker compose up -d              # PostGIS, Redis, Kafka
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

## Tests

```bash
cd backend
./mvnw test      # unit, web-slice (MockMvc + real JWT security) and ArchUnit tests; no Docker needed
./mvnw verify    # additionally runs *IT integration tests against a real PostGIS container
```

Integration tests extend `PostgisContainerSupport`. They are **skipped** (not failed) when Docker is
unavailable, so `verify` passing locally without Docker does not mean they ran: check the
`Skipped:` count in the output. CI always runs them.

| Suite | What it proves |
|---|---|
| `ArchitectureTest` | Layering rules: controllers never touch repositories/entities, no transactional controllers, etc. |
| `*WebTest` | HTTP contract: status codes, `ApiError` shape, validation, role rules, security headers, cookies |
| `*Test` (service/entity) | Business rules: refresh rotation and reuse detection, driver verification transitions, normalisation |
| `SchemaMigrationIT` | Flyway migrations apply, Hibernate mappings validate, DB constraints enforce invariants |
| `AuthAndOnboardingFlowIT` | End-to-end: register → login → refresh rotation → reuse detection; driver onboarding → admin verification; suspension |
| `DemoSeedIT` | Seed loads and pgcrypto-hashed demo passwords work with the application's password encoder |

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
| `REFRESH_COOKIE_SECURE` | no | `true` | `Secure` flag on the refresh cookie; `false` only for http://localhost |
| `REFRESH_COOKIE_SAME_SITE` | no | `Lax` | `None` (with Secure) if the frontend is on a different site than the API |
| `BCRYPT_STRENGTH` | no | `12` | BCrypt cost factor |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:3000` | Comma-separated browser origins allowed to call the API |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | no | — | Creates the first admin at startup if none exists |
| `DEMO_USER_PASSWORD` | **yes** with `demo` | — | Password for all seed accounts (hashed inside PostgreSQL; must not contain `'`) |
| `SERVER_PORT` | no | `8080` | API port |
| `MANAGEMENT_PORT` | no | `8081` | Actuator port; keep it off the public internet |
| `API_DOCS_ENABLED` | no | `true` (`false` in `prod`) | Swagger UI and `/v3/api-docs` |

## Conventions

- Controllers are thin: validate the DTO, read the principal (`@AuthenticationPrincipal AuthenticatedUser`), call one service method.
- Services own transactions. Entities expose intention-revealing methods (`driver.verify(...)`) instead of setters.
- Errors are `RideFlowException` subclasses with an `ErrorCode`; never return `null` or swallow exceptions.
- Every schema change is a new Flyway migration; never edit an applied one.
- Seed/demo data lives only in `db/seed` and is loaded only by the `demo` profile.
