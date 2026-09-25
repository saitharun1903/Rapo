# RideFlow — Measured performance

Only numbers from real runs are recorded here, with the machine and the conditions they were measured under.

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

## Scenario load tests (Phase 12)

**Run:** GitHub Actions `load-test` [run 36130629033](https://github.com/saitharun1903/Rapo/actions/runs/36130629033), commit `9605369`.

**Machine:** GitHub-hosted `ubuntu-latest` runner with 4 vCPU (AMD EPYC 7763), 15 GB RAM, Ubuntu 24.04.5. k6,
the backend, PostGIS, Redis and Kafka all shared that machine, so the load generator took CPU from the system
it measured.

**Stack:** the compose file's backend image and infrastructure, `prod` profile, BCrypt cost 12, a pool of 10
database connections, 3 partitions per Kafka topic. Three settings differ from production
([load-tests/README.md](../load-tests/README.md) explains each): rate limiting off, straight-line routing,
geocoding off.

**Load:** each scenario at 10, 50 and 100 VUs, closed loop (each VU waits for its response before the next
request), 60 s per run, one run at a time. Only the measured scenario counts: account setup and the nearby
scenario's background driver reports (30 drivers, every 5 s) are not in these numbers.

### Login

| VUs | Logins/s | p50 (ms) | p95 (ms) | p99 (ms) | Failed requests |
|---|---|---|---|---|---|
| 10 | 8.2 | 1186 | 2101 | 2703 | 0.00% |
| 50 | 8.9 | 6212 | 8610 | 10756 | 0.00% |
| 100 | 9.8 | 12274 | 19356 | 26497 | 0.00% |

### Nearby search

| VUs | Requests/s | p50 (ms) | p95 (ms) | p99 (ms) | Failed requests | Drivers per answer (median / p95) |
|---|---|---|---|---|---|---|
| 10 | 1491.2 | 5 | 13 | 23 | 0.00% | 9 / 13 |
| 50 | 1931.2 | 23 | 51 | 72 | 0.00% | 8 / 12 |
| 100 | 1872.1 | 49 | 112 | 158 | 0.00% | 8 / 11 |

### Ride creation (estimate, book, cancel)

| VUs | Bookings/s | Requests/s | `book` p50 (ms) | `book` p95 (ms) | `book` p99 (ms) | Failed requests |
|---|---|---|---|---|---|---|
| 10 | 198.8 | 596.4 | 19 | 38 | 54 | 0.00% |
| 50 | 334.4 | 1003.2 | 60 | 158 | 232 | 0.00% |
| 100 | 349.4 | 1048.2 | 119 | 327 | 485 | 0.00% |

p95 per step (ms):

| VUs | `estimate` | `book` | `cancel` |
|---|---|---|---|
| 10 | 21 | 38 | 34 |
| 50 | 21 | 158 | 152 |
| 100 | 21 | 327 | 317 |

### Full ride lifecycle

| VUs | Rides completed in 60 s | Requests/s | `book` p50 / p95 / p99 (ms) | Failed requests | Offers not received in 30 s |
|---|---|---|---|---|---|
| 10 | 1066 | 229.9 | 6 / 18 / 26 | 0.00% | 0 |
| 50 | 3304 | 737.7 | 26 / 68 / 115 | 0.00% | 0 |
| 100 | 2904 | 828.9 | 35 / 105 / 161 | 0.00% | 0 |

| VUs | Booking to offer seen, p50 / p95 (ms) | Booking to completed, p50 / p95 (ms) | p95 of `accept` / `arrive` / `start` / `complete` (ms) |
|---|---|---|---|
| 10 | 262 / 284 | 307 / 402 | 25 / 20 / 22 / 30 |
| 50 | 613 / 993 | 816 / 1275 | 78 / 76 / 75 / 81 |
| 100 | 1778 / 2619 | 2039 / 2949 | 110 / 103 / 103 / 111 |

"Booking to completed" is the backend's share of a ride: the simulated driver drives instantly, so no
real trip time is in it.

### How to read this

- **Nothing failed.** Every request of all 12 runs was answered as expected, and every lifecycle ride
  reached its driver within 30 s.
- **Login is CPU-bound by design.** Throughput stays at 8–10 logins/s whatever the load, and latency grows
  in step with the number of VUs waiting (10 VUs ÷ 8.2/s ≈ 1.2 s, the measured p50). Each login verifies a
  BCrypt hash at cost 12: at most about 0.4 core-seconds each (4 vCPUs ÷ 9.8 logins/s), less in fact, since k6
  and the rest of the stack used the same cores. That cost is what makes stolen
  password hashes expensive to crack, so it stays; login capacity comes from more instances, and in
  production the per-IP and per-email limits (off in this test) stop floods long before this point.
- **Nearby search levels off at about 1,900 answers/s.** From 50 VUs on, extra VUs add queueing, not
  throughput: p50 roughly doubles from 50 to 100 VUs while requests/s stays flat. The machine's 4 vCPUs,
  shared with k6, PostgreSQL and Kafka, are the likely limit; CPU was not recorded per process, so this is
  an inference from the flat throughput.
- **Writes, not reads, set the booking limit.** The fare estimate stays at a 21 ms p95 at every level,
  while booking and cancelling, the two write transactions, go from about 35 ms to over 300 ms. Each
  booking writes the ride, its history row and its outbox event in one transaction; the likely bottleneck
  is the pool of 10 database connections and PostgreSQL's commits on the shared disk. Pool waits were not
  recorded in this run, so that is not yet confirmed.
- **Matching falls behind at 100 VUs.** At 10 VUs a driver sees the offer 262 ms after booking, which
  includes up to 250 ms of the test's own polling interval. At 100 VUs it takes 1.8 s, and fewer rides
  complete than at 50 VUs (2,904 against 3,304): the ride steps themselves stay near 100 ms, so the time is
  spent waiting for matching. The `matching` listener sets no concurrency, so each backend instance
  consumes `ride.requested` on a single thread and runs matching rounds one at a time, although the topic
  has 3 partitions. That queue is the likely limit; the next step is listener concurrency equal to the
  partition count, measured with the same scenario. Consumer lag was not recorded in this run.
- **What these numbers are not.** One 4-vCPU machine running the load generator and every dependency is a
  floor, not a production capacity. Separate machines, a larger connection pool and more Kafka partitions
  would all move the limits above, and each change should be measured, not assumed.
