# RideFlow

A real-time ride-hailing platform: Spring Boot, PostgreSQL + PostGIS, Redis, Kafka, WebSocket, Next.js, and grounded AI trip insights.

> **Status: Phase 8 (Frontend).** Auth, driver onboarding, fares, PostGIS driver matching, the full ride lifecycle, the STOMP/WebSocket real-time channel, Redis caching and rate limiting (with [measured before/after numbers](docs/performance.md)), Kafka events (transactional outbox, matching, payments, notifications, batched location persistence, dead-letter topics), AI trip insights (local or external model, grounded and validated, with [recorded real runs](docs/ai.md)), and the Next.js web app for passengers, drivers and admins with a driver simulator and Playwright smoke tests are implemented and verified in CI. See the [implementation plan](docs/implementation-plan.md#progress). The full README will be written in Phase 15.

- [Development guide](docs/development.md): setup, tests, environment variables
- [Architecture](docs/architecture.md)
- [Database design](docs/database.md)
- [API contracts](docs/api.md)
- [Events and WebSocket channels](docs/events.md)
- [Measured performance](docs/performance.md)
- [Implementation plan](docs/implementation-plan.md)
