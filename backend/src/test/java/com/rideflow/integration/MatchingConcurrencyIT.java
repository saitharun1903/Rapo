package com.rideflow.integration;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.HUSSAIN_SAGAR;
import static com.rideflow.support.GeoTestPoints.offset;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.service.matching.MatchingSweeper;
import com.rideflow.support.MutableClock;
import com.rideflow.support.PostgisContainerSupport;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Races and time-driven matching: simultaneous accepts, offer expiry, radius growth and ride expiry. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RideTestConfig.class)
class MatchingConcurrencyIT extends PostgisContainerSupport {

    private static final Duration AWAIT = Duration.ofSeconds(10);
    private static final Duration PAST_OFFER_TTL = Duration.ofSeconds(21);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private MutableClock clock;
    @Autowired
    private MatchingSweeper sweeper;
    @Autowired
    private JdbcTemplate jdbc;

    private RideApi api;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
    }

    private String rideStatus(UUID rideId) {
        return jdbc.queryForObject("SELECT status FROM rides WHERE id = ?", String.class, rideId);
    }

    @Test
    void exactlyOneOfManySimultaneousAcceptsWins() throws Exception {
        Actor passenger = fixtures.passenger();
        List<Actor> drivers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
            assertThat(api.goOnline(driver, offset(HITECH_CITY, 100 + i * 100.0, 0)).getResponse().getStatus()).isEqualTo(200);
            drivers.add(driver);
        }
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> drivers.stream().allMatch(d -> {
            try {
                return api.openOfferCount(d) == 1;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }));

        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> attempts = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(drivers.size())) {
            for (Actor driver : drivers) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    return api.call(driver, "POST", "/api/rides/" + rideId + "/accept", null);
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> codes = new ArrayList<>();
            for (Future<MvcResult> attempt : attempts) {
                MvcResult result = attempt.get();
                statuses.add(result.getResponse().getStatus());
                if (result.getResponse().getStatus() != 200) {
                    codes.add(JsonPath.read(body(result), "$.code"));
                }
            }
            assertThat(statuses).containsOnlyOnce(200);
            assertThat(codes).hasSize(2).containsOnly("RIDE_ALREADY_ASSIGNED");
        }

        List<Map<String, Object>> offers = jdbc.queryForList(
                "SELECT status, count(*) AS n FROM ride_offers WHERE ride_id = ? GROUP BY status", rideId);
        assertThat(offers).extracting(row -> row.get("status") + "=" + row.get("n"))
                .containsExactlyInAnyOrder("ACCEPTED=1", "CANCELLED=2");
        Integer onTrip = jdbc.queryForObject("SELECT count(*) FROM drivers WHERE availability = 'ON_TRIP'", Integer.class);
        assertThat(onTrip).isEqualTo(1);
        assertThat(rideStatus(rideId)).isEqualTo("DRIVER_ASSIGNED");
    }

    @Test
    void expiredOfferMovesToNextRoundWithWiderRadius() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor ignoring = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        Actor distant = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        api.goOnline(ignoring, offset(HITECH_CITY, 500, 0));
        api.goOnline(distant, offset(HITECH_CITY, 3_800, 0)); // outside round 1 (3 km), inside round 2 (4.5 km)
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> api.openOfferCount(ignoring) == 1);
        assertThat(api.openOfferCount(distant)).isZero();

        clock.advance(PAST_OFFER_TTL);
        api.reportLocation(distant, offset(HITECH_CITY, 3_800, 0), clock.instant()); // keep position fresh
        sweeper.sweep();

        assertThat(api.openOfferCount(distant)).isEqualTo(1);
        assertThat(api.openOfferCount(ignoring)).isZero(); // same ride is never re-offered to the same driver
        assertThat(jdbc.queryForObject("SELECT matching_radius_m FROM rides WHERE id = ?", Integer.class, rideId))
                .isEqualTo(4_500);
        assertThat(jdbc.queryForObject("SELECT status FROM ride_offers WHERE ride_id = ? AND driver_id = ?",
                String.class, rideId, ignoring.id())).isEqualTo("EXPIRED");
    }

    @Test
    void rideExpiresAfterMaxRoundsWithoutDrivers() throws Exception {
        Actor passenger = fixtures.passenger();
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> "MATCHING".equals(rideStatus(rideId)));

        for (int round = 2; round <= 4; round++) {
            clock.advance(PAST_OFFER_TTL);
            sweeper.sweep();
        }

        assertThat(rideStatus(rideId)).isEqualTo("EXPIRED");
        List<String> timeline = JsonPath.read(body(api.call(passenger, "GET", "/api/rides/" + rideId + "/timeline", null)),
                "$[*].to");
        assertThat(timeline).containsExactly("REQUESTED", "MATCHING", "EXPIRED");
        // The passenger is free to book again.
        assertThat(api.call(passenger, "GET", "/api/rides/active", null).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void rejectingTheOnlyOfferImmediatelyTriesTheNextDriver() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor rejecting = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        api.goOnline(rejecting, offset(HITECH_CITY, 100, 0));
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> api.openOfferCount(rejecting) == 1);
        Actor other = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        api.goOnline(other, offset(HITECH_CITY, 200, 0));

        assertThat(api.call(rejecting, "POST", "/api/rides/" + rideId + "/reject", null).getResponse().getStatus())
                .isEqualTo(204);

        await().atMost(AWAIT).until(() -> api.openOfferCount(other) == 1);
    }
}
