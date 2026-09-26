# Raido — product benchmark and positioning

**Raido — intelligent real-time mobility platform.** This document turns the market research
([competitor-benchmark.md](competitor-benchmark.md)) and the code audit ([product-audit.md](product-audit.md)) into
product decisions: what Raido must have, where the market is weak, and where Raido is deliberately different.

## 1. Positioning

Raido is a mobility platform for one city at a time, built around three ideas:

1. **Real time you can see.** Live matching, live movement and live state, with honest connection status. The
   map is the product, not a background.
2. **Safety that notices.** A deterministic monitor watches every trip and asks, in neutral words, when something
   looks unusual. Every signal and every answer is recorded and explainable.
3. **Intelligence that shows its sources.** Every fare, ETA and AI answer is traceable to recorded facts. Raido's
   AI never invents a number; when it has no facts it says so.

Raido is not presented as a copy of any product. Its identity, words and layouts are its own.

## 2. Expected baseline

From the benchmark, a credible mobility product today has:

| Baseline item | Raido today | Plan |
|---|---|---|
| Live tracking on a map | Yes, but the car jumps and the camera fights the user | Phase 5 |
| Upfront price | Yes: signed quotes with a full breakdown | Keep |
| Categories | Car tiers only | Add bike and auto (Phase 10) |
| SOS, share trip, trusted contacts | No | Phase 6 |
| Boarding PIN / OTP | No | Phase 6 |
| Driver verification | Yes: admin verification with an audit trail | Keep |
| Two-way ratings | Yes | Keep |
| Scheduled rides | No | Phase 7 |
| Saved places, repeat trips | No | Phase 7 |
| Contact between rider and driver | No | In-ride chat (Phase 3) |
| Anomaly detection | No | Safety monitor (Phase 6) |
| Support | No | Phase 11 |

## 3. Weak points in the market, and Raido's answer

| Documented weak point | Raido's answer | How it is verifiable |
|---|---|---|
| Final charge above the fare shown; opaque algorithms | Signed quotes; the final fare uses the locked surge and the GPS-measured distance; both breakdowns side by side; the AI explanation may only cite recorded facts | Fare breakdown table; `AIResponseValidator` tests |
| Pressure to tip in advance | No tipping for priority. *Fast* is a published fee with a published effect on matching | The price-tier configuration is public in `application.yml` |
| ETA promises ("in 5 minutes or ₹50") | ETAs are labelled estimates, from the routing service or clearly marked as approximate | `estimateSource` on every quote |
| Membership dark patterns | No membership | — |
| Best-effort preference matching presented as a promise | Any preference says *not guaranteed* before booking and after matching | Copy review in the design system |
| Disputes without evidence | The trip timeline, offers, GPS distance and safety events are one auditable record | Admin incident timeline |

## 4. Differentiators (built)

1. **Explainable safety monitor.** Four deterministic signals (long stop, off-route, lost signal, overlong
   trip), each with a stated threshold, stored as a safety event with the rider's answer. The market's monitors
   are black boxes; Raido's thresholds are in configuration and documented.
2. **Shareable live trip with privacy by default.** The public page shows an approximate position (rounded) and
   only what a contact needs. The link is revocable and dies with the trip.
3. **Grounded Mobility Copilot.** Answers about the rider's own trips (spend, patterns, cheaper options) come
   from computed facts. It works with AI disabled; the model, when enabled, only rephrases validated facts.
4. **Real-time engineering on show.** Interpolated movement, a camera that respects the user, connection states
   that tell the truth, and an operations console fed by the same events.
5. **Driver transparency.** The platform fee is shown on every trip; acceptance and cancellation rates show their
   counts; the demand map hides cells with too little data.

## 5. Deliberately not built (and why)

| Feature | Reason | What exists instead |
|---|---|---|
| Real payments, cancellation fees | Needs a payment provider and compliance | Sandbox gateways; documented design |
| Shared rides | Needs pooled matching; faking co-riders is not acceptable | Roadmap entry |
| Parcel | A separate domain; scope | Domain design in the roadmap |
| Multi-stop | Touches the state machine, routing, fares and matching | Data model design (`ride_stops`) |
| Women-preference matching | Needs gender data, policy and legal review | Design notes: opt-in, configurable per market, never guaranteed |
| Ride recording | Consent, browser limits, regional law | Interface and a disabled-by-default setting |
| Automatic messages to contacts | No SMS or email provider | The device share sheet; `ContactNotifier` interface |
| Driver photos | Needs object storage | Monogram avatar with a verified badge |
| Transit in "best way to go" | No transit data source | Shown as unavailable |

## 6. Principles for every feature

- **Real data only.** No invented drivers, fares, ETAs, ratings, earnings, counts or AI output. In demo mode the
  driver simulator produces real rides through the public API.
- **Empty states are honest.** When there is too little data, Raido says so rather than showing a zero or a
  guess.
- **Neutral safety language.** "We noticed an unusual stop" rather than "Incident detected".
- **Participants only.** Ride data, chat, safety events and share links are authorised against the ride's
  participants; admins have their own role.
- **No precise location in public.** Share pages and demand maps round or aggregate.
- **The architecture stays.** One Spring Boot service, PostgreSQL with PostGIS, Redis, Kafka and STOMP. New
  Kafka topics only where asynchronous processing is needed.
