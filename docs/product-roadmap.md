# Raido product roadmap

The Raido transformation runs on the branch `feature/raido-product`. Phases are numbered R1–R16 so they do not
collide with the RideFlow engineering phases 1–15 in [implementation-plan.md](implementation-plan.md).

Each phase ends the way the engineering phases did:
1. compile;
2. unit and integration tests;
3. lint and type checks;
4. self-review;
5. documentation;
6. a commit;
7. a CI run where the phase changes the backend or the end-to-end flow.

Nothing is marked done without having been run.

## Phases

| Phase | Scope | Exit criteria |
|---|---|---|
| **R1 Audit** | [product-audit.md](product-audit.md), [competitor-benchmark.md](competitor-benchmark.md), [product-benchmark.md](product-benchmark.md), [feature-spec.md](feature-spec.md), this roadmap | Documents reviewed against the code; no application change |
| **R2 Design system and shell** | Raido brand, tokens (light and dark), type scale, motion rules, core components (bottom sheet, sheet header, icon button, stat, list rows), app shell, [design-system.md](design-system.md) | Every existing page renders in both themes with the new tokens; frontend tests, lint, types and build pass |
| **R3 Passenger** | State-driven ride panel (feature spec §1), driver card, mobile bottom sheet, in-ride chat (§3, backend and frontend), privacy fix (§4.8) | Unit tests per state; chat integration test (HTTP and STOMP); privacy integration test; e2e booking still passes |
| **R4 Driver** | Driver console redesign (offer, trip steps, summary), driver trip history, mobile layout | Driver e2e still passes |
| **R5 Map** | Map theme provider, light and dark styles, marker interpolation, camera modes, locate and recenter, approach route, route progress, error state (§2) | Unit tests for interpolation, camera and route split; the map draws in the production build in e2e for both themes |
| **R6 Safety** | Safety events, SOS, share trip and its public page, trusted contacts, boarding PIN, safety monitor, arrival check-in, report issue (§4) | Integration tests: PIN gate, share privacy and expiry, monitor signals with synthetic tracks, SOS idempotence, authorisation; e2e: PIN flow and share page |
| **R7 Places and scheduling** | Saved places, ride again, scheduled rides with dispatcher and reminders (§5–7) | Integration tests: constraints, dispatch through the normal booking path, cancellation, failure reasons |
| **R8 Driver intelligence** | Online sessions, stats, today against the week, demand map, driver safety entry (§9) | Integration tests for every aggregate against known data, including empty states |
| **R9 Copilot** | Intent rules, mobility summary, template answers, optional model rephrasing through the validator (§10) | Unit tests per intent; validator tests; works with AI disabled |
| **R10 Mobility options** | Bike and auto categories, best way to go, smart pickup; price tiers if time allows (§8, 11, 12) | Pricing tests; e2e books a bike or auto ride with a simulated driver |
| **R11 Support and operations** | Support center, admin safety and support queues, funnel, live map, admin cancel, incident timeline (§14–15) | Integration tests for admin authorisation and cancel from every state |
| **R12 Website** | Product website with an interactive, clearly labelled demo | Lighthouse accessibility ≥ 95; no fake metrics |
| **R13 Responsive** | 320, 375, 390, 430, 768, 1024 and 1440 px checks, bottom-sheet behaviour | Screenshots at each width |
| **R14 Testing and accessibility** | Keyboard paths, screen-reader labels, contrast, reduced motion; fill test gaps (map, live ride, notifications) | axe checks in e2e; coverage gates still met |
| **R15 Performance** | Bundle size, map re-renders, request counts, k6 on new endpoints | Before/after numbers in performance.md |
| **R16 Production verification** | Deploy on the free tier (needs the owner's accounts); end-to-end checks on the public URL | Checked on the live URL, or listed as unverifiable |

## Deliberately deferred

The following are designed in [feature-spec.md §16](feature-spec.md#16-deferred-designs), not built:
- multi-stop;
- shared rides;
- parcel;
- women-preference matching;
- ride recording (interface only);
- cancellation fees;
- driver pre-acceptance of scheduled rides;
- push, SMS and email delivery;
- real payments;
- DLT replay.

Each needs infrastructure, policy or legal input that a portfolio deployment does not have, and faking any of them
would break the real-data rule.

## Naming

- The product surface (UI, website, docs titles, README) becomes **Raido**.
- Internal identifiers keep their names: the `com.rideflow` Java package, `rideflow.*` configuration and metric
  names, image names and Kafka topics.
- Renaming them would touch every file, dashboard and deployment for no user-visible gain, and would break the
  deployed configuration.
