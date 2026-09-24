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

Scenario load tests (login, ride creation, nearby search, lifecycle, concurrency at 10/50/100 VUs) come in Phase 12.
