package com.rideflow.integration;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.HUSSAIN_SAGAR;
import static com.rideflow.support.GeoTestPoints.line;
import static com.rideflow.support.GeoTestPoints.offset;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoMath;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.support.MutableClock;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.KafkaTestSupport;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The full ride journey over HTTP against real PostGIS: quote, booking, asynchronous matching, accept
 * race, geofenced arrival, GPS-tracked trip and final fare. No mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RideTestConfig.class)
class RideLifecycleIT extends IntegrationTestContainers {

    private static final Duration AWAIT = Duration.ofSeconds(10);
    private static final Duration GPS_INTERVAL = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private MutableClock clock;
    @Autowired
    private KafkaTestSupport kafka;

    private RideApi api;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
    }

    private static DocumentContext json(MvcResult result) throws Exception {
        return JsonPath.parse(body(result));
    }

    private static void assertStatus(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
    }

    private static void assertError(MvcResult result, int status, String code) throws Exception {
        assertStatus(result, status);
        assertThat(json(result).read("$.code", String.class)).isEqualTo(code);
    }

    @Test
    void passengerRequestsAndDriverCompletesATrackedTrip() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor nearDriver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        Actor fartherDriver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        Actor bystander = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        GeoPoint nearStart = offset(HITECH_CITY, 300, 0);
        assertStatus(api.goOnline(nearDriver, nearStart), 200);
        assertStatus(api.goOnline(fartherDriver, offset(HITECH_CITY, -1_500, 0)), 200);

        // Quote + booking
        MvcResult estimate = api.call(passenger, "POST", "/api/fares/estimate",
                "{\"pickup\":%s,\"dropoff\":%s}".formatted(RideApi.point(HITECH_CITY), RideApi.point(HUSSAIN_SAGAR)));
        assertStatus(estimate, 200);
        assertThat(json(estimate).read("$.quotes.length()", Integer.class)).isEqualTo(3);
        assertThat(json(estimate).read("$.estimateSource", String.class)).isEqualTo("APPROXIMATE");
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);

        // Matching runs asynchronously after commit and offers the ride to both nearby drivers.
        await().atMost(AWAIT).until(() -> api.openOfferCount(nearDriver) == 1 && api.openOfferCount(fartherDriver) == 1);
        assertStatus(api.call(fartherDriver, "GET", "/api/rides/" + rideId, null), 200);
        assertError(api.call(bystander, "GET", "/api/rides/" + rideId, null), 404, "RIDE_NOT_FOUND");
        assertError(api.call(passenger, "POST", "/api/rides", "{}"), 400, "VALIDATION_FAILED");

        // First accept wins; the other driver is told the ride is taken.
        MvcResult accepted = api.call(nearDriver, "POST", "/api/rides/" + rideId + "/accept", null);
        assertStatus(accepted, 200);
        assertThat(json(accepted).read("$.status", String.class)).isEqualTo("DRIVER_ASSIGNED");
        assertError(api.call(fartherDriver, "POST", "/api/rides/" + rideId + "/accept", null), 409, "RIDE_ALREADY_ASSIGNED");
        MvcResult active = api.call(passenger, "GET", "/api/rides/active", null);
        assertThat(json(active).read("$.driver.id", String.class)).isEqualTo(nearDriver.id().toString());
        assertThat(json(active).read("$.driver.vehicle.plateNumber", String.class)).startsWith("TS");

        // The state machine refuses skipping ahead; arrival is geofenced on the driver's real position.
        assertError(api.call(nearDriver, "POST", "/api/rides/" + rideId + "/start", null), 409, "RIDE_INVALID_TRANSITION");
        assertStatus(api.call(nearDriver, "POST", "/api/rides/" + rideId + "/en-route", null), 200);
        assertError(api.call(nearDriver, "POST", "/api/rides/" + rideId + "/arrive", null), 422, "NOT_AT_PICKUP");
        assertStatus(api.reportLocation(nearDriver, offset(HITECH_CITY, 40, 0), clock.instant()), 202);
        assertStatus(api.call(nearDriver, "POST", "/api/rides/" + rideId + "/arrive", null), 200);

        // Trip: GPS reports along the road are sampled into the track.
        assertStatus(api.call(nearDriver, "POST", "/api/rides/" + rideId + "/start", null), 200);
        GeoPoint[] route = line(offset(HITECH_CITY, 40, 0), HUSSAIN_SAGAR, 12);
        for (int i = 1; i < route.length; i++) {
            clock.advance(GPS_INTERVAL);
            assertStatus(api.reportLocation(nearDriver, route[i], clock.instant()), 202);
        }
        // Positions reach PostgreSQL through the batching Kafka consumer; let it catch up so the trail is complete.
        kafka.awaitIdle();
        MvcResult completed = api.call(nearDriver, "POST", "/api/rides/" + rideId + "/complete", null);
        assertStatus(completed, 200);

        DocumentContext ride = json(completed);
        double expectedDistance = GeoMath.haversineMeters(route[0], HUSSAIN_SAGAR);
        assertThat(ride.read("$.status", String.class)).isEqualTo("COMPLETED");
        assertThat(ride.read("$.actual.distanceSource", String.class)).isEqualTo("TRACKED");
        assertThat(ride.read("$.actual.distanceMeters", Integer.class)).isCloseTo((int) expectedDistance,
                within((int) (expectedDistance * 0.01)));
        assertThat(ride.read("$.actual.durationSeconds", Integer.class)).isEqualTo(11 * 15);
        assertThat(ride.read("$.actual.fare.amount", String.class)).isNotBlank();
        assertThat(ride.read("$.actual.breakdown.pricingVersion", String.class)).isEqualTo("2026-09-v1");

        // Aftermath: driver is free again, history and timeline are complete, terminal ride is immutable.
        assertThat(json(api.call(nearDriver, "GET", "/api/drivers/me", null)).read("$.availability", String.class))
                .isEqualTo("AVAILABLE");
        List<String> timeline = json(api.call(passenger, "GET", "/api/rides/" + rideId + "/timeline", null)).read("$[*].to");
        assertThat(timeline).containsExactly("REQUESTED", "MATCHING", "DRIVER_ASSIGNED", "DRIVER_ARRIVING",
                "DRIVER_ARRIVED", "IN_PROGRESS", "COMPLETED");
        DocumentContext history = json(api.call(passenger, "GET", "/api/rides", null));
        assertThat(history.read("$.content[0].id", String.class)).isEqualTo(rideId.toString());
        assertThat(history.read("$.content[0].fareIsFinal", Boolean.class)).isTrue();
        assertError(api.call(passenger, "POST", "/api/rides/" + rideId + "/cancel", null), 409, "RIDE_INVALID_TRANSITION");
    }

    @Test
    void passengerCannotHoldTwoActiveRides() throws Exception {
        Actor passenger = fixtures.passenger();
        api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);

        MvcResult estimate = api.call(passenger, "POST", "/api/fares/estimate",
                "{\"pickup\":%s,\"dropoff\":%s}".formatted(RideApi.point(HITECH_CITY), RideApi.point(HUSSAIN_SAGAR)));
        String quoteId = JsonPath.<List<String>>read(body(estimate), "$.quotes[*].quoteId").getFirst();
        MvcResult second = api.call(passenger, "POST", "/api/rides", """
                {"quoteId":"%s","pickup":{"point":%s,"address":"A"},"dropoff":{"point":%s,"address":"B"},"paymentMethod":"CASH"}
                """.formatted(quoteId, RideApi.point(HITECH_CITY), RideApi.point(HUSSAIN_SAGAR)));

        assertError(second, 409, "ACTIVE_RIDE_EXISTS");
    }

    @Test
    void quoteCannotBeUsedByAnotherPassengerOrForAnotherPlace() throws Exception {
        Actor owner = fixtures.passenger();
        Actor thief = fixtures.passenger();
        MvcResult estimate = api.call(owner, "POST", "/api/fares/estimate",
                "{\"pickup\":%s,\"dropoff\":%s}".formatted(RideApi.point(HITECH_CITY), RideApi.point(HUSSAIN_SAGAR)));
        String quoteId = JsonPath.<List<String>>read(body(estimate), "$.quotes[*].quoteId").getFirst();
        String bookingTemplate = """
                {"quoteId":"%s","pickup":{"point":%s,"address":"A"},"dropoff":{"point":%s,"address":"B"},"paymentMethod":"CASH"}
                """;

        assertError(api.call(thief, "POST", "/api/rides", bookingTemplate.formatted(quoteId,
                RideApi.point(HITECH_CITY), RideApi.point(HUSSAIN_SAGAR))), 422, "QUOTE_INVALID");
        assertError(api.call(owner, "POST", "/api/rides", bookingTemplate.formatted(quoteId,
                RideApi.point(offset(HITECH_CITY, 500, 0)), RideApi.point(HUSSAIN_SAGAR))), 422, "QUOTE_MISMATCH");
        clock.advance(Duration.ofMinutes(6));
        assertError(api.call(owner, "POST", "/api/rides", bookingTemplate.formatted(quoteId,
                RideApi.point(HITECH_CITY), RideApi.point(HUSSAIN_SAGAR))), 422, "QUOTE_EXPIRED");
    }

    @Test
    void estimateOutsideServiceAreaIsRejected() throws Exception {
        Actor passenger = fixtures.passenger();
        GeoPoint mumbai = new GeoPoint(19.0760, 72.8777);

        assertError(api.call(passenger, "POST", "/api/fares/estimate",
                "{\"pickup\":%s,\"dropoff\":%s}".formatted(RideApi.point(HITECH_CITY), RideApi.point(mumbai))),
                422, "OUTSIDE_SERVICE_AREA");
    }

    @Test
    void driverWithdrawingBeforePickupRedispatchesToAnotherDriver() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor first = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        assertStatus(api.goOnline(first, offset(HITECH_CITY, 200, 0)), 200);
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> api.openOfferCount(first) == 1);
        assertStatus(api.call(first, "POST", "/api/rides/" + rideId + "/accept", null), 200);

        Actor second = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        assertStatus(api.goOnline(second, offset(HITECH_CITY, 400, 0)), 200);
        MvcResult withdrawn = api.call(first, "POST", "/api/rides/" + rideId + "/cancel", "{\"reason\":\"Flat tyre\"}");

        assertStatus(withdrawn, 200);
        assertThat(json(withdrawn).read("$.status", String.class)).isEqualTo("MATCHING");
        await().atMost(AWAIT).until(() -> api.openOfferCount(second) == 1);
        assertThat(api.openOfferCount(first)).isZero();
        assertStatus(api.call(second, "POST", "/api/rides/" + rideId + "/accept", null), 200);
        assertThat(json(api.call(first, "GET", "/api/drivers/me", null)).read("$.availability", String.class))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void passengerCancellationReleasesTheDriver() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        assertStatus(api.goOnline(driver, offset(HITECH_CITY, 200, 0)), 200);
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> api.openOfferCount(driver) == 1);
        assertStatus(api.call(driver, "POST", "/api/rides/" + rideId + "/accept", null), 200);

        MvcResult cancelled = api.call(passenger, "POST", "/api/rides/" + rideId + "/cancel", "{\"reason\":\"Plans changed\"}");

        assertStatus(cancelled, 200);
        assertThat(json(cancelled).read("$.cancellation.cancelledBy", String.class)).isEqualTo("PASSENGER");
        assertThat(json(api.call(driver, "GET", "/api/drivers/me", null)).read("$.availability", String.class))
                .isEqualTo("AVAILABLE");
        assertError(api.call(driver, "POST", "/api/rides/" + rideId + "/en-route", null), 409, "RIDE_INVALID_TRANSITION");
    }
}
