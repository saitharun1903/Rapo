# RideFlow — Measured performance

Only numbers from real runs are recorded here, with the machine and the conditions they were measured under. The full scenario load tests (login, ride creation, nearby search, lifecycle, 10/50/100 VUs) are Phase 12.

## Cache benchmark (Phase 5)

**Run:** GitHub Actions `cache-benchmark` [run 36044436543](https://github.com/saitharun1903/Rapo/actions/runs/36044436543), commit `875148e`.

**Machine:** GitHub-hosted `ubuntu-latest` runner with 4 vCPU (AMD EPYC 7763) and 15 GB RAM. The backend, PostGIS, Redis, the upstream stand-ins and k6 all shared that machine.

**Load:**
- `POST /api/fares/estimate`: 20 VUs, closed loop, for 60 s per mode.
- `GET /api/geo/search`: 5 VUs over the same period.
- The same packaged backend ran with `CACHE_ENABLED=false`, then `true`.
- Rate limiting was disabled so it would not cap the load.

**Upstream:** OSRM and Nominatim were replaced by WireMock stand-ins. Each answered after the median of 5 real requests made from the same runner just before the run: OSRM 403 ms, Nominatim 59 ms. The public servers must not be load-tested; see [load-tests/README.md](../load-tests/README.md).

**Workload:** trips between 12 well-known Hyderabad landmarks (132 possible trips), and searches for 12 place names.

| Endpoint | Cache | Requests/s | p50 (ms) | p95 (ms) | p99 (ms) | Error rate |
|---|---|---|---|---|---|---|
| `POST /api/fares/estimate` | off | 40.3 | 409.8 | 744.8 | 928.6 | 0.00% |
| `POST /api/fares/estimate` | on | 2228.3 | 6.8 | 17.2 | 25.0 | 0.00% |
| `GET /api/geo/search` | off | 41.2 | 62.3 | 372.7 | 463.9 | 0.00% |
| `GET /api/geo/search` | on | 924.6 | 4.3 | 11.3 | 17.7 | 0.00% |

Cache outcomes during the cached run, from the backend's own `rideflow_cache_requests_total` counter:

| Cache | Hits | Misses | Hit rate |
|---|---|---|---|
| route | 133,515 | 186 | 99.9% |
| surge | 133,683 | 18 | 100.0% |
| geocode | 55,462 | 16 | 100.0% |

### How to read this

- **Latency.** With caching on, the median estimate takes 6.8 ms instead of 410 ms. Almost all of the uncached time is the router call: its p50 is the 403 ms stand-in delay plus about 7 ms of our own work. The saving scales with real router latency; a self-hosted OSRM in the same data centre would be much faster than the public one measured here.
- **Throughput.** The "off" requests/s is not the server's capacity. Closed-loop VUs wait for each response, so 20 VUs × ~0.41 s gives about 40 requests/s. The "on" figure is closer to what the backend itself sustains on this 4-vCPU machine, still with k6 and all dependencies on the same box.
- **Hit rate.** A small set of popular places is the favourable case for these caches. With pickups spread across the whole city, route hits would be lower. Surge hits would stay high, because it is cached per ~1 km cell. The hit rates above are what this workload produced, not a production forecast.
- **Concurrent first requests.** There were 186 route misses for 132 distinct trips: VUs that asked for the same new trip at the same moment each called the router. No request coalescing (single-flight) is implemented. At this scale the cost is a few dozen extra upstream calls during warm-up.
- **Not explained.** Uncached search has a p95 of 373 ms against a 59 ms stand-in delay, while its p50 matches the delay. It was not investigated; likely candidates are contention in the stand-in or on the shared runner while it serves ~16 concurrent delayed routing responses. It does not affect the cached results.
