# Competitor benchmark

What seven ride-hailing products offer today, taken from their own pages. It is used only to find the expected
baseline, the documented weak points and the room for Raido to be different ([product-benchmark.md](product-benchmark.md)).
Nothing here is copied into Raido: no names, text, icons, layouts or flows.

**Method.** Every claim comes from an official source: the company's site, help centre, newsroom or blog, legal
terms, its Google Play or App Store listing, or (for Namma Yatri) its official open-source repository. Weak points
also cite government regulators and, where labelled *reported*, reputable news. All pages were read on
2026-09-26. **NV** means *not verified from an official source*, not *absent*. Features vary by market; the market
is noted where a source is market-specific. Pages that failed to load are listed at the end.

## Products at a glance

| | Markets in sources | Model in one line |
|---|---|---|
| **Uber** | US, India, global | Upfront algorithmic fares with surge built in; in India, autos run on a driver-subscription ("SaaS") model where the driver sets the final fare |
| **Rapido** | India | Bike taxis first, then autos and cabs; terms describe a lead-generation platform with an estimated fare *range* |
| **Ola** | India | Upfront fares with peak pricing the rider must confirm; broad category list and rentals |
| **Namma Yatri** | India (7 cities) | Open-source, zero commission; riders pay drivers directly by UPI or cash |
| **Lyft** | US, Canada | Upfront fares; price tiers from "wait and save" to priority pickup |
| **Bolt** | Europe, Africa, others | Upfront estimates with dynamic pricing; drivers may set their own rate within a band |
| **inDrive** | Global | The rider names a price and drivers accept or counter; positioned as "no surge" |

## Comparison matrix

Yes / Partial / No / NV. Notes in brackets. Source keys refer to the list at the end.

| Feature | Uber | Rapido | Ola | Namma Yatri | Lyft | Bolt | inDrive |
|---|---|---|---|---|---|---|---|
| Two- and three-wheelers (bike, auto) | Yes [U1, U-PLAY] | Yes [R-PLAY] | Yes [O-PLAY] | Yes, auto [NY-PLAY] | No (US) | NV | NV |
| Car tiers (economy, premium, XL) | Yes [U1] | Partial, cab | Yes [O-PLAY] | Partial, cab | Yes [L1] | Yes [B1] | Partial, city |
| Pet rides | Yes [U1] | Yes, auto [R-PLAY] | NV | NV | Yes [L2] | Yes [B1] | Partial |
| Wheelchair-accessible / assisted | Yes [U11] | NV | NV | NV | Yes [L3] | NV | NV |
| Scheduled rides | Yes, to 90 days [U2] | Yes [R2] | Yes [O-PLAY] | Partial, repeat | Yes, to 90 days [L5] | Yes, to 90 days [B1] | Partial, intercity [I4] |
| Upfront algorithmic price | Yes [U3] | Partial, range [R3] | Yes [O2] | Partial | Yes [L6] | Yes [B1] | No, negotiated |
| Surge / dynamic pricing | Yes [U3] | NV | Yes [O2] | NV | Yes [L6] | Yes [B2] | No [I-PLAY] |
| Rider- or driver-set price | Partial [U4] | Partial [R-PLAY] | Partial [O6] | NV | No | Partial [B4] | Yes [I-PLAY] |
| Rider subscription | Yes [U2] | Partial, dated [R4] | Partial, dated [O4] | NV | Yes [L1, L7] | Yes [B11] | NV |
| Published cancellation-fee policy | Yes [U3] | Yes [R3] | Yes [O3] | NV | Yes [L6] | Yes [B3] | NV |
| SOS / emergency | Yes [U6] | Yes [R3] | Yes [O-IOS] | Yes [NY-PLAY] | Yes [L8] | Yes [B5] | Yes [I5] |
| Share trip / live link | Yes [U6] | Yes [R2] | Yes [O-IOS] | Yes [NY-PLAY] | Yes [L8] | Yes [B5] | Yes [I5] |
| Trusted / emergency contacts | Yes [U6] | Partial [R3] | Yes, up to 5 [O-IOS] | Yes [NY-PLAY] | Yes [L8] | Yes [B5] | Yes [I5] |
| Boarding PIN / OTP | Yes [U6] | Yes, one static PIN [R-PLAY] | Yes, OTP [O-PLAY] | Partial [NY-GH1] | Partial, some markets [L8] | Yes [B5] | Partial |
| Automatic trip-anomaly detection | Yes [U6] | NV | Yes, dated source [O5] | Partial, in code [NY-GH1] | Yes [L8] | Yes [B5] | Partial, pre-trip [I6] |
| Audio / video recording | Yes [U6, U8] | NV | NV | Partial, in code [NY-GH2] | Yes [L8] | Yes, audio [B-PLAY] | NV |
| Women-preference matching | Yes, not guaranteed [U5] | NV | NV | NV | Yes, not guaranteed [L9] | Yes [B1] | NV |
| Driver ID / background checks | Yes [U7] | Yes [R5] | Partial, dated [O5] | NV | Yes [L8] | Partial, selfie ID [B5] | Yes [I5] |
| Rider verification for drivers | Partial [U8] | NV | NV | NV | Yes [L11] | Yes, 2026 [B7] | Yes [I6] |
| Masked contact | Yes [U7] | NV | Partial, dated [O5] | NV | Yes [L8] | Yes [B5] | Yes [I5] |
| Live tracking | Yes | Yes | Yes | Yes, lock-screen [NY-IOS] | Yes | Yes | Yes |
| Driver demand guidance | Yes, AI summary [U9] | NV | NV | NV | Partial, bonus map [L11] | Yes [B9] | NV |
| Commission-free / driver subscription | Partial, India auto [U4] | Yes [R5] | Partial, reported [N2] | Yes [NY-PLAY] | No, 70 % floor [L10] | No [B8] | Partial, ~10 % [I3] |
| Pooled rides | Yes [U1] | Yes, auto share [R-PLAY] | NV | Partial | NV | NV | Partial |
| Accessibility programme | Yes [U11, U12] | NV | NV | NV | Yes [L3, L4] | Partial [B12] | NV |
| Multimodal (transit, micromobility) | Yes [U13] | Yes, metro [R-PLAY] | NV | Yes, metro and bus [NY-PLAY] | Yes [L-PLAY] | Yes [B-PLAY] | No |
| Parcel / courier | Yes [U15] | Yes [R-PLAY] | Yes [O-PLAY] | NV | NV | Yes [B-PLAY] | Yes [I1] |
| Loyalty / membership | Yes [U2] | Partial, coins [G3] | Yes, coins [O-PLAY] | NV | Yes [L1] | Yes [B11] | NV |
| Saved places / favourites / repeat | Yes [U16] | Yes [R-PLAY] | NV | Yes [NY-PLAY] | Yes [L14] | NV | NV |
| Rider-facing AI | Yes, voice booking [U14] | NV | Partial, affiliate [O10] | NV | Yes, support agent [L12, L13] | Yes, ChatGPT booking [B13] | Partial, ML ETAs [I7] |

## Notes per dimension

**Booking and categories.** Indian products lead with two- and three-wheelers (bike taxi, auto) and add cabs;
US and European products lead with car tiers and add pets, accessibility and electric options. Uber and Rapido
list first/last-mile links to metro stations.

**Scheduling.** Uber, Lyft and Bolt take bookings up to 90 days ahead. Uber's reservations carry a booking fee
and no surge and can be cancelled free up to an hour before [U2]; Lyft locks the price and allows edits up to an
hour before [L5]. inDrive schedules only intercity trips [I4].

**Pricing.** Four models exist side by side:
- algorithmic upfront fares with surge (Uber, Lyft, Bolt, Ola);
- an estimate *range* with the fare agreed between rider and driver (Rapido's terms);
- rider-named prices with counter-offers (inDrive);
- zero-commission direct payment (Namma Yatri, Uber Auto in India).

Lyft sells price tiers on the same trip: cheaper if the rider accepts a longer wait, dearer for a priority
pickup [L1].

**Safety.** Every product has SOS, trip sharing and contacts. The differences are in detection and verification:
- Uber, Lyft and Bolt detect unusual stops or route changes and check in with the rider [U6, L8, B5]. Ola
  announced an ML monitor in 2018 [O5].
- Namma Yatri's public code has night-time checks, checks for unexpected events during and after the ride,
  shake-to-SOS, a false-alarm counter and a safety drill mode [NY-GH1, NY-GH2].
- Bolt records audio encrypted on the device and deletes it after 24 hours (blog snippet) [B6]. Uber's audio
  cannot be played by Uber staff [U6].
- Rider verification for drivers (selfie and ID) arrived at Bolt in 2026 [B7] and exists at inDrive [I6].

**Women-preference matching.** Uber and Lyft both say a match is not guaranteed. Lyft reports matching about
two thirds of the time [L9]. Uber lets drivers in 40 countries set the preference [U5].

**Driver experience.**
- Uber's 2026 driver release adds an AI summary of busy areas, a trip guide, rating and cancellation-rate appeals
  ("metrics fairness") and chat protection [U9].
- Lyft guarantees drivers at least 70 % of rider payments weekly and added an AI earnings assistant [L10, L11].
- In India the driver subscription model is spreading (Rapido, Namma Yatri, Uber Auto, and Ola as reported).

**Support.** Lyft runs an AI support agent built on Claude via Amazon Bedrock and reports an 87 % cut in resolution
time [L12, L13]. Uber uses generative AI in earner support [U10]. All offer 24/7 safety lines except where NV.

**Multimodal and delivery.** Metro tickets and first/last-mile rides in India (Rapido, Namma Yatri); transit and
micromobility in Uber, Lyft and Bolt. Parcel delivery is offered by six of seven.

**Loyalty.** Paid memberships (Uber One, Lyft Pink, Bolt Plus) and coin schemes (Ola, Rapido).

**AI.** Conversational booking is new in 2026: Uber Voice Bookings [U14] and Bolt booking from ChatGPT with an
upfront estimate and suggested pickup point, handing off to the app to confirm [B13]. None of the sources
describes an assistant that answers questions from the rider's own trip history.

**Map experience.** Not separately documented in official sources beyond live tracking. Namma Yatri shows the trip
on the iOS lock screen [NY-IOS].

## Documented weak points

These are complaints, notices and cases on public record. Notices and lawsuits are allegations, not findings.

1. **Fare fairness.**
   - India's consumer regulator (CCPA) sent Ola and Uber notices on grievance handling, cancellation charges and
     fare algorithms [G1].
   - It sent a second notice in 2025 over reported iPhone/Android price differences, which both companies
     denied [G2].
   - The top complaint category against Ola in 2024 was a final charge above the fare shown at booking [G4].
2. **Advance tipping for faster pickup.** CCPA called it unfair in a notice to Uber [G5]. The probe was reported
   to extend to Ola and Rapido [N3].
3. **ETA promises.** CCPA fined Rapido over "auto in 5 minutes or ₹50" advertising, where the ₹50 came as
   short-lived coins and the conditions were unreadable [G3].
4. **Membership cancellation.** The US FTC sued Uber in 2025 over Uber One enrolment and cancellation [G6].
5. **Driver pay claims.** The FTC settled with Lyft in 2024 over misleading driver-earnings claims [G7].
6. **Serious safety incidents at scale.** Reported in Uber's own US safety report [U17].
7. **Disputes under driver-subscription models.** Uber does not step into Uber Auto fare disputes in India [U4].
   Rapido's terms disclaim the accuracy of fare estimates [R3].
8. **Preference matching is best-effort.** See women-preference matching above [U5, L9].
9. **Regulatory volatility.** Karnataka's bike-taxi suspension in 2025 and its later reversal (*reported*) [N4, N5].

## Sources

All accessed 2026-09-26.

**Uber**
- [U1] https://www.uber.com/us/en/ride/ride-options/
- [U2] https://www.uber.com/us/en/ride/how-it-works/reserve/
- [U3] https://www.uber.com/us/en/ride/how-it-works/upfront-pricing/
- [U4] https://www.uber.com/in/en/blog/uber-auto-is-now-on-saas-what-does-that-mean-for-riders/
- [U5] https://www.uber.com/us/en/safety/womens-safety/
- [U6] https://www.uber.com/us/en/ride/safety/
- [U7] https://www.uber.com/us/en/safety/
- [U8] https://www.uber.com/us/en/drive/safety/
- [U9] https://www.uber.com/us/en/newsroom/only-on-uber-2026/
- [U10] https://www.uber.com/us/en/legal/generative-ai-features-at-uber/
- [U11] https://www.uber.com/us/en/about/accessibility/
- [U12] help.uber.com: features for deaf and hard-of-hearing drivers
- [U13] help.uber.com: how to purchase a (transit) ticket
- [U14] https://www.uber.com/us/en/newsroom/go-get-2026/
- [U15] https://www.uber.com/us/en/item-delivery/
- [U16] help.uber.com: saved places; favourite driver
- [U17] https://www.uber.com/us/en/about/reports/us-safety-report/
- [U-PLAY] https://play.google.com/store/apps/details?id=com.ubercab

**Rapido**
- [R-PLAY] https://play.google.com/store/apps/details?id=com.rapido.passenger
- [R-IOS] https://apps.apple.com/us/app/rapido-bike-taxi-auto-cabs/id1198464606
- [R2] https://rapido.bike/bangalore/main-page
- [R3] https://www.rapido.bike/CustomerTerms
- [R4] https://www.rapido.bike/Press
- [R5] https://www.rapido.bike/CaptainTerms

**Ola**
- [O-PLAY] https://play.google.com/store/apps/details?id=com.olacabs.customer
- [O-IOS] https://apps.apple.com/us/app/ola-book-cab-auto-bike-taxi/id539179365
- [O1] https://www.olacabs.com/
- [O2] https://help.olacabs.com/support/dreport/205097581
- [O3] https://help.olacabs.com/support/dreport/208298769
- [O4] https://blog.olacabs.com/get-the-ola-cab-pass-and-enjoy-zero-peak-pricing-2/
- [O5] olacabs.com press release: Ola Guardian real-time ride monitoring (2018)
- [O6] https://blog.olacabs.com/driver-partner-bidding-model-tc-auto/
- [O10] tech.olakrutrim.com Kruti blog (failed to load; search snippet only)

**Namma Yatri**
- [NY-PLAY] https://play.google.com/store/apps/details?id=in.juspay.nammayatri
- [NY-IOS] https://apps.apple.com/in/app/namma-yatri/id1637429831
- [NY-GH] https://github.com/nammayatri/nammayatri
- [NY-GH1] the repository's safety settings specification (`Backend/lib/shared-services/spec/Safety/Storage/SafetySettings.yaml`)
- [NY-GH2] the repository's SOS specification (`Backend/lib/shared-services/spec/Safety/Storage/Sos.yaml`)

**Lyft**
- [L-PLAY] https://play.google.com/store/apps/details?id=me.lyft.android
- [L1] https://www.lyft.com/rider
- [L2] help.lyft.com: pet rides for riders
- [L3] help.lyft.com: WAV rides; Lyft Assisted rides
- [L4] https://www.lyft.com/rider/silver
- [L5] https://www.lyft.com/ride-with-lyft/scheduledrides
- [L6] https://www.lyft.com/terms; help.lyft.com: cancel and no-show policy
- [L7] https://www.lyft.com/rider/commute/pricelock
- [L8] https://www.lyft.com/safety
- [L9] help.lyft.com: Women+ Connect for riders; Lyft blog, nationwide launch
- [L10] help.lyft.com: earnings commitment
- [L11] Lyft blog: new driver improvements
- [L12] Lyft blog: AWS and Lyft, agentic AI for riders and drivers
- [L13] Lyft blog: Lyft and Anthropic
- [L14] help.lyft.com: how to change your address

**Bolt**
- [B-PLAY] https://play.google.com/store/apps/details?id=ee.mtakso.client
- [B1] https://bolt.eu/en/rides/
- [B2] bolt.eu support: dynamic pricing
- [B3] bolt.eu support: cancellation fee
- [B4] bolt.eu support: driver-set rates
- [B5] https://bolt.eu/en/rides/safety/
- [B6] Bolt blog: audio trip recording (search snippet only)
- [B7] Bolt blog: rider verification
- [B8] bolt.eu driver guide: commissions
- [B9] https://bolt.eu/en/driver/earn/ (search snippet)
- [B10] https://bolt.eu/en/driver/safety/
- [B11] https://bolt.eu/en/plus/
- [B12] Bolt blog: inclusive e-bike
- [B13] Bolt blog: ChatGPT integration

**inDrive**
- [I-PLAY] https://play.google.com/store/apps/details?id=sinet.startup.inDriver
- [I1] https://indrive.com/en-us/fair-services
- [I3] inDrive blog: US launch
- [I4] indrive.com help: schedule a ride in advance
- [I5] https://indrive.com/safety/passengers
- [I6] https://indrive.com/safety/drivers
- [I7] inDrive blog: super-app expansion (Feb 2026)

**Regulators**
- [G1] https://www.pib.gov.in/Pressreleaseshare.aspx?PRID=1826940
- [G2] https://ddnews.gov.in/en/ccpa-issues-notices-to-ola-uber-over-differential-pricing-apple-also-under-scrutiny/
- [G3] https://www.pib.gov.in/PressReleseDetailm.aspx?PRID=2158830
- [G4] https://www.pib.gov.in/PressReleasePage.aspx?PRID=2064519
- [G5] https://www.newsonair.gov.in/centre-issues-notice-to-uber-over-advance-tip-issue
- [G6] FTC press releases, April and December 2025 (Uber One)
- [G7] FTC press release, October 2024 (Lyft driver earnings)

**Reputable news (labelled *reported*)**
- [N2] MediaNama, June 2025: Ola's zero-commission model
- [N3] Deccan Herald: CCPA probe into advance tipping
- [N4] Deccan Herald: Karnataka bike-taxi suspension
- [N5] LiveLaw: Karnataka High Court on the bike-taxi ban

**Failed to load or incomplete.**
- **Rapido:** the safety page (rendered by JavaScript), the safety-guidelines PDF (too large) and the help centre
  (TLS error).
- **Namma Yatri:** the website pages (rendered by JavaScript).
- **Ola:** the driver portal and the Krutrim blog.
- **inDrive:** the safety redirect.
- **Bolt:** two blog posts (bodies not rendered).
