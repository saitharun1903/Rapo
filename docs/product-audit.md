# Product audit

What the repository actually does today, feature by feature, checked in the code rather than the README, and what
Raido should do about each gap. It is the input to the roadmap ([product-roadmap.md](product-roadmap.md)) and the
feature specifications ([feature-spec.md](feature-spec.md)). The market column refers to
[competitor-benchmark.md](competitor-benchmark.md).

**Baseline.** Branch `feature/raido-product`, cut from `phase-15` at 84c31f4, whose CI was green on c414504
(e2e, free-tier-fit, frontend-ci, secret-scan) and on d1985d3 (backend-ci, all integration tests).

**Keys.**
- **Status:** ✅ present, 🟡 partial, ❌ absent.
- **Priority:** P0 essential, P1 high value, P2 differentiator, P3 future.
- **Complexity:** S (days), M (a week), L (more).

## 1. What already exists (verified in code)

The engineering base is strong and is kept as it is.

- **Backend.** Spring Boot 4.1 on Java 21. PostgreSQL with PostGIS, managed by Flyway V1–V7. Redis, Kafka with a
  transactional outbox and dead-letter topics, and STOMP over a native WebSocket.
- **Ride lifecycle.** Nine states (REQUESTED, MATCHING, DRIVER_ASSIGNED, DRIVER_ARRIVING, DRIVER_ARRIVED,
  IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED) with one transition table (`RideStateMachine`). Every change is
  recorded in `ride_status_events`.
- **Matching.** PostGIS nearest-neighbour rounds with a widening radius (3 km × 1.5ⁿ, capped at 8 km), 3 offers per
  round, a 20 s offer TTL and 3 rounds before the ride expires. Offer exclusivity is enforced by the database.
- **Fares.** Signed fare quotes, live surge from demand and supply in a geohash cell, and a final fare from the
  GPS-measured distance.
- **Payments.** Settled asynchronously. The CASH and SANDBOX card gateways capture every payment; no money moves.
- **Driver location pipeline.** STOMP, then Redis, then Kafka, then batched PostgreSQL writes. There is no
  database write per ping.
- **Privacy.**
  - Passengers see nearby cars rounded to about 110 m, with no identity.
  - Only the ride's participants can see a ride or its live location.
  - Sentry events are scrubbed.
- **AI Trip Intelligence.**
  - Runs asynchronously after completion.
  - Facts carry no personal data.
  - Every number the model writes must match a supplied fact.
  - Providers: disabled, local (Ollama) or Anthropic, behind a circuit breaker and a bulkhead.
- **Frontend.** Next.js 16 with Tailwind 4. Light and dark themes (the map itself is light only). Typed API client
  generated from the OpenAPI document. A STOMP client with jittered backoff and snapshot refetch.
- **Consoles.** A driver console and earnings page. An admin console with overview, rides, drivers, users, system
  and audit pages.
- **Tooling.**
  - Simulator: drivers, plus optional passengers.
  - Docker Compose with Prometheus and Grafana.
  - k6 scenarios.
- **Tests.**
  - Backend: 69 test classes, 16 of them Testcontainers integration classes.
  - Frontend: 21 Vitest files.
  - End to end: 4 Playwright specs.
  - CI: 7 workflows.

## 2. Feature audit

### 2.1 Map and ride experience

| Feature | Current implementation | Status | Market | Gap | Recommendation | Pri | Cx | Depends on |
|---|---|---|---|---|---|---|---|---|
| Map style | OpenFreeMap *liberty* for every theme (`lib/config.ts`) | 🟡 | Every product has a branded map | Busy style, no dark map; the only provider switch is a URL | A light and a dark style chosen by theme (OpenFreeMap *positron* and *dark*), restyled at runtime to Raido's palette and label hierarchy, behind a `MapTheme` provider interface | P0 | M | design system |
| Driver movement | The marker jumps to each fix (`MapView.tsx`) | ❌ | Smooth movement everywhere | Cars teleport about 22 m every 2 s in the demo | Interpolate between fixes with `requestAnimationFrame`, move the marker without re-rendering the map, and rotate by heading | P0 | S | — |
| Camera | Refits on every driver update and overrides a manual pan | 🟡 | — | Fights the user | Fit once per phase change; pause following after the user interacts; add a recenter control | P0 | S | — |
| Locate / recenter controls | Panel buttons only | ❌ | Baseline | — | Map controls: locate me (with an accuracy circle), recenter on the trip | P0 | S | — |
| Route while the driver approaches | No route line while the driver comes to pickup | ❌ | Baseline | The passenger cannot see the approach | Driver-to-pickup route, re-queried at most every 30 s or 150 m | P0 | S | `/api/geo/route` (rate limit 60/min) |
| Route progress | Static pickup-to-dropoff line | 🟡 | Baseline | No sense of progress | Split the line into travelled and remaining at the driver's projected position | P1 | S | — |
| Map error state | `console.error` and a data attribute only | 🟡 | — | A failed map is invisible to users | A visible error panel with retry; the trip panel stays usable without the map | P0 | S | — |
| State-driven ride UI | All nine states handled (`ActiveRideView`, `RideOutcome`) | 🟡 | Baseline | Plain panel; no motion; no route while arriving | Redesign per state (see feature spec §1) with the same data | P0 | M | design system |
| Mobile layout | A map-above-panel grid | 🟡 | A bottom sheet over the map | No bottom sheet | Bottom sheet with snap points on small screens; floating panel on desktop | P0 | M | design system |
| Driver identity card | Name, rating, vehicle, plate; no photo (`DriverCard`) | 🟡 | Photo, plate, rating | No photo storage in the backend | Monogram avatar plus a verified badge; photos deferred (they need object storage) | P1 | S | — |
| Contacting the other party | None | ❌ | Masked calls and in-app chat are baseline | Neither side can reach the other | In-app ride chat over the existing STOMP channel, participants only, closed when the ride ends; masked calling deferred (needs telephony) | P1 | M | WebSocket |
| Connection states | idle, connecting, connected, reconnecting, unavailable; banner for the last two | 🟡 | — | No offline state, no "restored" message | Add *offline* (from `navigator.onLine`) and a short "Connection restored" only after a real outage | P2 | S | — |

### 2.2 Safety

| Feature | Current implementation | Status | Market | Gap | Recommendation | Pri | Cx | Depends on |
|---|---|---|---|---|---|---|---|---|
| Safety Center (rider and driver) | None | ❌ | All seven | — | A shield button on every active ride that opens a sheet with SOS, share, PIN, the verified driver or vehicle, report an issue and emergency information | P1 | M | the items below |
| SOS | None | ❌ | All seven | — | Record an auditable `SOS` safety event with the last known position, show emergency numbers (112 in India) as `tel:` links, and alert admins live. Raido never claims to contact emergency services itself | P1 | M | safety events |
| Share my trip | None | ❌ | All seven | — | Time-limited, revocable public link to a read-only page: status, approximate position, pickup and drop-off area, vehicle and plate. It expires when the ride ends | P1 | M | `trip_shares` |
| Trusted contacts | None | ❌ | Six of seven | No SMS or email provider exists | Store contacts with a sharing policy (every ride, night rides, manual). Raido prepares the link and opens the device share sheet; automatic delivery waits for a notification provider behind a `ContactNotifier` interface (disabled by default) | P1 | M | trip shares |
| Boarding PIN | None | ❌ | Five of seven | — | A 4-digit PIN per ride, shown only to the passenger. The driver must enter it before `DRIVER_ARRIVED → IN_PROGRESS` when the feature is on. Attempts are limited and audited | P1 | S | — |
| Ride safety monitor | None | ❌ | Uber, Lyft, Bolt, Ola | — | Deterministic sweeper over IN_PROGRESS rides: long stop, off-route, lost signal, overlong trip. Neutral check-in ("We noticed an unusual stop. Are you okay?"); every signal and answer is stored | P1 | L | planned route, safety events |
| Post-trip check-in | None | ❌ | Lyft, Namma Yatri (code) | — | Optional "Did you get there safely?" after night rides, configurable; a missed answer is only recorded, never escalated automatically | P2 | S | safety events |
| Report an issue | None | ❌ | Baseline | — | Creates a support ticket linked to the ride | P1 | S | support tickets |
| Ride recording | None | ❌ | Uber, Lyft, Bolt | Browser limits, consent and regional law | Interface and a disabled-by-default setting only; no fake recordings | P3 | S | — |
| Women-preference matching | None | ❌ | Uber, Lyft, Bolt, all best-effort | Needs gender data, policy and legal review | Design only: opt-in, policy- and market-configurable, never guaranteed; not built now | P3 | L | policy decision |
| Location privacy | Fuzzed nearby cars; participant-only tracking | ✅ | — | A driver with only a *pending* offer can read the passenger's full name through `GET /api/rides/{id}` (`RideViewAssembler`) | Show the passenger to a driver only after acceptance | P0 | S | — |

### 2.3 Booking, pricing and personalisation

| Feature | Current implementation | Status | Market | Gap | Recommendation | Pri | Cx | Depends on |
|---|---|---|---|---|---|---|---|---|
| Categories | ECONOMY, COMFORT, XL | 🟡 | Indian products lead with bike and auto | No two- or three-wheelers | Add BIKE and AUTO (pricing, vehicle rules, seats, simulator). Right for Hyderabad | P1 | M | migration, pricing config |
| Saved places | None | ❌ | Uber, Lyft, Rapido, Namma Yatri | — | Home, Work and named favourites: saved, renamed, removed, used as pickup or destination | P1 | S | — |
| Ride again | None | ❌ | Namma Yatri, Uber | — | From history: reuse the places, category and payment method and **always re-quote**; never reuse a stale price | P1 | S | — |
| Scheduled rides | None | ❌ | Five of seven | — | Persisted schedule with its own status. A sweeper books the real ride through the normal quote and matching path at a lead time. Reminders go through notifications. Driver pre-acceptance deferred | P1 | L | notifications |
| Price tiers (Save / Standard / Fast) | One price per category | ❌ | Lyft's wait-and-save and priority | — | Configurable tiers: *Save* has a discount and starts matching after a delay in a smaller radius; *Fast* has a priority fee and starts with a larger radius and more offers per round. Shown only when the configuration enables them | P2 | M | matching, fare |
| Explained fares | Estimate and final breakdown; AI explanation | ✅ | Weak point across the market (CCPA notices) | — | Keep; make "why the fare changed" prominent in the completed state | P1 | S | — |
| Multi-stop | None | ❌ | Uber, Lyft | Touches routing, fares, the state machine and matching | Deferred with a design (`ride_stops`) | P3 | L | — |
| Shared rides | None | ❌ | Uber, Rapido | Needs pooled matching | Deferred; no fake co-riders | P3 | L | — |
| Parcel | None | ❌ | Six of seven | Separate domain | Deferred with a separate domain design, never inside `Ride` | P3 | L | — |
| Pet / large vehicle / accessibility | Vehicles have category and seats only | ❌ | Uber, Lyft, Bolt | No vehicle capabilities | Vehicle capabilities plus ride preferences used as matching filters; no claims beyond what a driver declared | P2 | M | matching |
| Cancellation fees | None | ❌ | Baseline | — | Deferred: payments are sandbox-only | P3 | M | payments |

### 2.4 Driver

| Feature | Current implementation | Status | Market | Gap | Recommendation | Pri | Cx | Depends on |
|---|---|---|---|---|---|---|---|---|
| Earnings | Total, trips and a chart for 24 h / 7 d / 30 d (`/api/drivers/me/earnings`) | 🟡 | Baseline | No per-hour or per-trip comparison | Today against this week and the average of previous days, earnings per online hour | P1 | S | online sessions |
| Acceptance / cancellation rate | Not computed | ❌ | Uber appeals these metrics | — | From `ride_offers` and `ride_status_events`; shown with counts and an honest empty state below a minimum sample | P1 | S | — |
| Online time | Not recorded | ❌ | — | — | `driver_online_sessions` written on online and offline (including presence time-outs) | P1 | S | migration |
| Demand map | Surge exists internally only | ❌ | Uber, Bolt | — | Aggregated demand and supply per geohash-6 cell (about 1.2 × 0.6 km, the cell surge already uses) (low / normal / high). Cells below a k-anonymity threshold are hidden; no passenger data | P1 | M | Redis cache |
| Trip history | None for drivers | ❌ | Baseline | — | The existing `GET /api/rides` already serves drivers; add a page | P1 | S | — |
| Driver safety | None | ❌ | Uber, Lyft, Bolt, inDrive | — | The same Safety Center with the driver's view: SOS, share, the passenger's verified PIN step, report | P1 | S | safety |

### 2.5 AI and mobility intelligence

| Feature | Current implementation | Status | Market | Gap | Recommendation | Pri | Cx | Depends on |
|---|---|---|---|---|---|---|---|---|
| Trip Intelligence | Per-ride facts, observations, grounded analysis, questions | ✅ | No competitor documents this | — | Keep; surface it in the completed state | — | — | — |
| Mobility Copilot | None | ❌ | Uber, Bolt: booking by conversation | No questions across trips | Grounded Copilot over `UserMobilitySummary` (spend by month, time of day and category, typical trip, cheaper options). Deterministic answers for supported intents, with optional model phrasing through the same number validator. Works with AI disabled | P2 | L | AI service |
| Best way to go | None | ❌ | Multimodal elsewhere | No transit data source | Compare Raido's categories with real quotes, plus walking time for short trips; transit shown as unavailable, never invented | P2 | M | categories |
| Smart pickup | None | ❌ | Bolt suggests pickup points | — | Deterministic: snap to the nearest routable road through the routing service and suggest it if it is more than 25 m away, with the distance and reason | P2 | M | routing |

### 2.6 Support and operations

| Feature | Current implementation | Status | Market | Gap | Recommendation | Pri | Cx | Depends on |
|---|---|---|---|---|---|---|---|---|
| Support center | None | ❌ | Baseline | — | Tickets with a category, priority and status, linked to a ride; an admin queue; safety tickets first | P2 | M | — |
| Admin operations | Overview, rides, drivers, users, system, audit, live feed | ✅ | — | No safety or support queue, funnel or live map | Add a safety events queue, support queue, offer funnel (from `ride_offers`) and a live map of active rides | P2 | M | the above |
| Admin ride intervention | ADMIN has no ride transitions; IN_PROGRESS cannot be cancelled | ❌ | — | Operations cannot end a stuck ride | Admin cancel with a reason (audited), including IN_PROGRESS | P2 | S | — |
| Incident timeline | `ride_status_events` shown as a timeline | ✅ | — | Offers and safety events are separate | Merge offers, status events and safety events into one admin timeline | P2 | S | safety events |
| DLT replay | Counts only | 🟡 | — | — | Deferred | P3 | M | — |

### 2.7 Website and brand

| Feature | Current implementation | Status | Market | Gap | Recommendation | Pri | Cx | Depends on |
|---|---|---|---|---|---|---|---|---|
| Brand | "RideFlow", violet and coral, Plus Jakarta Sans | 🟡 | — | Reads as a template | Raido identity: ink and paper with one signal colour; see the design system | P0 | M | — |
| Website | One hero, four feature cards, CTAs | 🟡 | — | Not a product site | Product website with an interactive map demo (labelled *demo*, fixed example data), ride-state walkthrough, safety, Copilot demo, architecture, stack, FAQ | P2 | L | design system, map |
| Notifications off-app (push, SMS, email) | In-app only | ❌ | Baseline | No provider | Deferred; `ContactNotifier` interface | P3 | M | a provider |
| Payments | Sandbox only | 🟡 | — | No real gateway | Out of scope for a portfolio project; documented | P3 | L | — |

## 3. Bugs and risks found during the audit

| Finding | Evidence | Action |
|---|---|---|
| A driver with only a pending offer can read the passenger's full name | `RideViewAssembler` always includes `passenger`; the offer payload itself does not | Fix in Phase 6 (privacy); covered by a test |
| The camera refits on every driver update | `ActiveRideView` passes the live driver point into `fitTo` | Fixed by the map work in Phase 5 |
| Map failures are invisible | `MapView` logs only | Fixed in Phase 5 |
| An IN_PROGRESS ride cannot be ended by anyone but its driver | `RideStateMachine` | Admin cancel, Phase 11 |
| `PENDING`, `FAILED` and `REFUNDED` payment statuses are never set | `PaymentService` | Documented; out of scope |

The items still open from the Phase 15 audit remain in [implementation-plan.md](implementation-plan.md):
- Outbox relay under a Kafka outage.
- Payment reconciliation.
- Surge during an outage.
- Offer rejection without a lock.
- BCrypt inside the login transaction.
- The rate limiter fails open (by design).
- Access tokens are not revoked at logout.

## 4. Priorities in one list

| Priority | Features |
|---|---|
| **P0** | Raido brand and design system; map themes, smooth movement, camera and controls, error state; state-driven ride UI and mobile bottom sheet; the pending-offer privacy fix |
| **P1** | Safety Center (SOS, share trip, trusted contacts, boarding PIN, report issue, safety monitor); in-ride chat; saved places; ride again; scheduled rides; bike and auto categories; driver command center (acceptance, cancellations, online time, today against the week) and demand map; driver safety |
| **P2** | Mobility Copilot; best way to go; smart pickup; price tiers; vehicle capabilities and ride preferences; support center; admin operations (safety and support queues, funnel, live map, admin cancel); post-trip check-in; offline state; product website |
| **P3** | Multi-stop; shared rides; parcel; women-preference matching; ride recording; cancellation fees; push, SMS or email; real payments; DLT replay |

The roadmap orders these into delivery phases and records what is deliberately left out.
