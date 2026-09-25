# Load tests (k6)

Raw k6 output is written to `load-tests/results/`, which is git-ignored. Only numbers from real runs are copied into the docs, together with the machine they ran on.

## Cache benchmark (Phase 5)

`cache-benchmark/` measures what the Redis caches buy for fare estimates and place search. It runs the same packaged backend twice under the same load: once with `CACHE_ENABLED=false`, once with `true`.

| File | Purpose |
|---|---|
| `cache-benchmark.js` | k6 script: 20 VUs request estimates between 12 Hyderabad landmarks; 5 VUs search 12 place names |
| `measure-upstreams.sh` | Times 5 real requests each to the public OSRM and Nominatim servers and prints the medians |
| `stub-templates/` | WireMock stand-ins for OSRM and Nominatim, answering after the measured median delay |
| `run.sh` | Starts the backend in each mode, runs k6, and scrapes the backend's cache counters |
| `report.py` | Turns both k6 summaries into one Markdown table, with cache hit rates |

**Why stand-ins for the upstream services.** Load-testing the public OSRM and Nominatim servers would break their usage policies, and their latency varies with things we don't control. The stand-ins return fixed answers after the latency just measured from the same machine. Everything else is real: the Spring Boot backend, PostgreSQL/PostGIS and Redis. The upstream latency is therefore realistic, but its variance is not modelled.

**Workload assumption.** Passengers usually pick popular places, so trips repeat. With 12 places there are 132 possible trips, and after warm-up nearly every estimate is a cache hit. This is the favourable case for caching; a city-wide spread of pickups would hit less often. The report shows the hit rate that was actually observed.

**Running it.** The benchmark runs in GitHub Actions (`.github/workflows/cache-benchmark.yml`) on every change to `load-tests/cache-benchmark/`, or on manual dispatch. To run it locally you need Docker, Java 21, Python 3 and k6. Follow the same steps as the workflow:

1. Package the backend.
2. Start PostGIS, Redis and the stand-ins.
3. Export `DB_PASSWORD`, `JWT_SECRET`, `OSRM_DELAY_MS` and `NOMINATIM_DELAY_MS`.
4. Run `bash load-tests/cache-benchmark/run.sh` from the repository root.

## Scenario load tests (Phase 12)

`scenarios/` puts the main paths of the app under load, each at 10, 50 and 100 virtual users (VUs), one
run at a time, for 60 seconds per run. Results are in [docs/performance.md](../docs/performance.md).

| Scenario | Each iteration | Measured |
|---|---|---|
| `login.js` | A passenger signs in | `POST /api/auth/login`: a BCrypt check at the production cost (12) |
| `nearby.js` | A passenger looks for cars around a random point in the centre, while 30 online drivers report positions every 5 s in the background | `GET /api/drivers/nearby`: a PostGIS query over positions that reached PostgreSQL through Kafka |
| `booking.js` | A passenger gets a quote, books with it and cancels while matching (one active ride per passenger) | `POST /api/rides`, plus the estimate and the cancellation |
| `lifecycle.js` | One passenger and one driver per VU: book, matching offers the ride through Kafka, the driver polls their offers, accepts, reports GPS at the pickup, arrives, starts, reports GPS at the dropoff and completes | Every step, the time from booking to the driver seeing the offer, and from booking to completion |

| File | Purpose |
|---|---|
| `lib.js` | Configuration, account creation through the public API (register, log in, onboard, verify, go online), points in the service area |
| `run.sh` | Runs every scenario at every level in k6's container |
| `report.py` | Turns the k6 summaries into the Markdown tables |

**Only the measured scenario counts.** Accounts are created in k6's `setup()` (in parallel batches, since
each registration and login hashes a password), and the drivers' background position reports run as a
separate scenario. Neither is in the reported numbers: every script names its measured scenario `load`, and
the report reads only its sub-metrics.

**Lifecycle drivers do not compete.** Each VU's driver waits on its own cell of a 3.2 km grid, wider than
the first matching round's 3 km radius, so round one offers each ride only to that VU's driver. A ride
whose offer does not arrive within 30 s is counted and cancelled. Each load level uses new cells, because
drivers of the previous level may still be online.

**What differs from production, and why.** Everything else is the production configuration (`prod`
profile, BCrypt cost 12, a pool of 10 database connections).

- **Rate limiting is off.** k6 sends everything from one address, which the per-IP limits (5 logins a
  minute) would stop within seconds. With them on, the test would measure the limiter instead of the system.
- **Routing is straight-line.** Load-testing the public OSRM server would break its usage policy, and
  its latency is not ours. The cache benchmark above measured what routing latency costs.
- **Geocoding is off.** None of the scenarios searches for places.

**Limits of the numbers.** k6 and the whole stack share one GitHub runner, so the load generator takes CPU
from the system it measures, and PostgreSQL, Kafka and Redis sit on the same machine as the backend. A
lifecycle's times include the driver's polling interval (250 ms). Requests per second are divided by the
60-second run length; a ride still in progress at the end may finish during a 60-second grace period.

**Running it.** In GitHub Actions (`.github/workflows/load-test.yml`) on every change to
`load-tests/scenarios/`, or on manual dispatch. Locally, with Docker: start the stack with the settings
above (the workflow's "Create .env" step shows them), then
`ADMIN_EMAIL=... ADMIN_PASSWORD=... bash load-tests/scenarios/run.sh`. `SCENARIOS`, `LEVELS` and
`DURATION` narrow a run.
