package com.rideflow.integration;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.HUSSAIN_SAGAR;
import static com.rideflow.support.GeoTestPoints.offset;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.service.driver.DriverPresenceSweeper;
import com.rideflow.support.MutableClock;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.RealtimeTestConfig;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import com.rideflow.support.StompTestClient;
import com.rideflow.support.StompTestClient.Connection;
import com.rideflow.support.SubscriptionProbe;
import com.rideflow.websocket.WebSocketSessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Real-time channel over a real WebSocket against the running server and PostGIS: who receives what,
 * what is refused, and how stale drivers and expired tokens are handled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RideTestConfig.class, RealtimeTestConfig.class})
class RealtimeIT extends IntegrationTestContainers {

    private static final String RIDES = "/user/queue/rides";
    private static final String RIDE_LOCATION = "/user/queue/ride-location";
    private static final String RIDE_OFFERS = "/user/queue/ride-offers";
    private static final String PRESENCE = "/user/queue/presence";
    private static final String ERRORS = "/user/queue/errors";
    private static final String NOTIFICATIONS = "/user/queue/notifications";
    private static final String ADMIN_ACTIVITY = "/topic/admin/activity";
    private static final String DRIVER_LOCATION = "/app/drivers/location";
    private static final Duration PRESENCE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    /** The default CORS_ALLOWED_ORIGINS. */
    private static final String FRONTEND_ORIGIN = "http://localhost:3000";
    private static final String FOREIGN_ORIGIN = "https://evil.example";

    @LocalServerPort
    private int port;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private MutableClock clock;
    @Autowired
    private SubscriptionProbe probe;
    @Autowired
    private DriverPresenceSweeper presenceSweeper;
    @Autowired
    private WebSocketSessionRegistry sessionRegistry;
    @Autowired
    private DriverLocationRepository driverLocations;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MeterRegistry meters;

    private RideApi api;
    private StompTestClient stomp;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
        stomp = new StompTestClient(port, probe);
    }

    @AfterEach
    void tearDown() {
        stomp.close();
    }

    private static DocumentContext json(MvcResult result) throws Exception {
        return JsonPath.parse(body(result));
    }

    private static void assertStatus(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
    }

    private Map<String, Object> location(GeoPoint point, Instant recordedAt) {
        return Map.of("location", Map.of("lat", point.lat(), "lng", point.lng()),
                "headingDeg", 90, "recordedAt", recordedAt.toString());
    }

    private Actor onlineDriver(GeoPoint at) throws Exception {
        Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        assertStatus(api.goOnline(driver, at), 200);
        return driver;
    }

    private Connection subscribed(Actor actor, String... destinations) throws Exception {
        Connection connection = stomp.connect(actor);
        for (String destination : destinations) {
            connection.subscribe(destination);
        }
        return connection;
    }

    private static boolean isRide(JsonNode message, UUID rideId) {
        return rideId.toString().equals(message.get("rideId").asString());
    }

    private static boolean hasStatus(JsonNode ride, UUID rideId, String status) {
        return rideId.toString().equals(ride.get("id").asString()) && status.equals(ride.get("status").asString());
    }

    /** Books a ride and has {@code driver} accept the offer pushed to them; returns the ride id. */
    private UUID assignedRide(Actor passenger, Actor driver, Connection driverSocket) throws Exception {
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        driverSocket.next(RIDE_OFFERS, offer -> isRide(offer, rideId) && "OFFER".equals(offer.get("type").asString()));
        assertStatus(api.call(driver, "POST", "/api/rides/" + rideId + "/accept", null), 200);
        return rideId;
    }

    @Test
    void offerStatusAndDriverLocationReachOnlyTheRideParticipants() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor bystander = fixtures.passenger();
        Actor driver = onlineDriver(offset(HITECH_CITY, 300, 0));
        Connection driverSocket = subscribed(driver, RIDE_OFFERS, RIDES, RIDE_LOCATION, ERRORS);
        Connection passengerSocket = subscribed(passenger, RIDES, RIDE_LOCATION, NOTIFICATIONS);
        Connection bystanderSocket = subscribed(bystander, RIDES, RIDE_LOCATION);

        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);

        // The offer is pushed to the driver with what they need to decide.
        JsonNode offer = driverSocket.next(RIDE_OFFERS, message -> isRide(message, rideId));
        assertThat(offer.get("type").asString()).isEqualTo("OFFER");
        assertThat(offer.get("offer").get("distanceToPickupMeters").asInt()).isCloseTo(300, within(5));
        assertThat(offer.get("offer").get("estimatedFare").get("amount").asString()).isNotBlank();

        // Accepting pushes the full ride view, with the driver, to both participants.
        assertStatus(api.call(driver, "POST", "/api/rides/" + rideId + "/accept", null), 200);
        JsonNode assigned = passengerSocket.next(RIDES, ride -> hasStatus(ride, rideId, "DRIVER_ASSIGNED"));
        assertThat(assigned.get("driver").get("id").asString()).isEqualTo(driver.id().toString());
        assertThat(assigned.get("version").asLong()).isPositive();
        driverSocket.next(RIDES, ride -> hasStatus(ride, rideId, "DRIVER_ASSIGNED"));
        // The notifications consumer stored a notification; the realtime bridge pushed it.
        JsonNode notification = passengerSocket.next(NOTIFICATIONS);
        assertThat(notification.get("type").asString()).isEqualTo("DRIVER_ACCEPTED");
        assertThat(notification.get("rideId").asString()).isEqualTo(rideId.toString());
        assertThat(notification.get("read").asBoolean()).isFalse();

        // A GPS fix streamed over the socket reaches the passenger (through Kafka) and is persisted in a batch.
        GeoPoint moved = offset(HITECH_CITY, 200, 0);
        driverSocket.send(DRIVER_LOCATION, location(moved, clock.instant()));
        JsonNode pushed = passengerSocket.next(RIDE_LOCATION);
        assertThat(pushed.get("rideId").asString()).isEqualTo(rideId.toString());
        assertThat(pushed.get("location").get("lat").asDouble()).isCloseTo(moved.lat(), within(1e-9));
        assertThat(pushed.get("location").get("lng").asDouble()).isCloseTo(moved.lng(), within(1e-9));
        assertThat(pushed.get("headingDeg").asInt()).isEqualTo(90);
        await().atMost(StompTestClient.TIMEOUT).untilAsserted(() -> assertThat(
                driverLocations.find(driver.id()).orElseThrow().point().lat()).isCloseTo(moved.lat(), within(1e-7)));

        // The snapshot endpoint agrees and adds an ETA to the pickup.
        DocumentContext tracking = json(api.call(passenger, "GET", "/api/rides/" + rideId + "/tracking", null));
        assertThat(tracking.read("$.stale", Boolean.class)).isFalse();
        assertThat(tracking.read("$.driverLocation.point.lat", Double.class)).isCloseTo(moved.lat(), within(1e-7));
        assertThat(tracking.read("$.eta.target", String.class)).isEqualTo("PICKUP");
        assertThat(tracking.read("$.eta.source", String.class)).isEqualTo("APPROXIMATE");
        assertThat(tracking.read("$.eta.distanceMeters", Integer.class)).isPositive();

        // The snapshot cached the ETA, so the next location push carries it without another routing call.
        clock.advance(Duration.ofSeconds(1));
        driverSocket.send(DRIVER_LOCATION, location(offset(HITECH_CITY, 150, 0), clock.instant()));
        JsonNode withEta = passengerSocket.next(RIDE_LOCATION);
        assertThat(withEta.get("eta").get("target").asString()).isEqualTo("PICKUP");
        assertThat(withEta.get("eta").get("seconds").asInt())
                .isEqualTo(tracking.read("$.eta.seconds", Integer.class));

        // Nobody else hears about this ride or this driver.
        bystanderSocket.assertNothingReceived(RIDES);
        bystanderSocket.assertNothingReceived(RIDE_LOCATION);
        driverSocket.assertNothingReceived(RIDE_LOCATION);
        driverSocket.assertNothingReceived(ERRORS);
        assertStatus(api.call(bystander, "GET", "/api/rides/" + rideId + "/tracking", null), 404);
    }

    @Test
    void acceptingWithdrawsOtherOffersAndCancellationReachesTheDriver() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor winner = onlineDriver(offset(HITECH_CITY, 300, 0));
        Actor other = onlineDriver(offset(HITECH_CITY, -600, 0));
        Connection winnerSocket = subscribed(winner, RIDE_OFFERS, RIDES);
        Connection otherSocket = subscribed(other, RIDE_OFFERS, RIDES);

        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        winnerSocket.next(RIDE_OFFERS, message -> isRide(message, rideId));
        otherSocket.next(RIDE_OFFERS, message -> isRide(message, rideId));
        assertThat(json(api.call(passenger, "GET", "/api/rides/" + rideId + "/tracking", null)).read("$.code", String.class))
                .isEqualTo("TRACKING_UNAVAILABLE");

        assertStatus(api.call(winner, "POST", "/api/rides/" + rideId + "/accept", null), 200);

        // The losing driver's app drops the offer at once instead of waiting for it to expire.
        JsonNode withdrawn = otherSocket.next(RIDE_OFFERS, message -> isRide(message, rideId));
        assertThat(withdrawn.get("type").asString()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.get("offer").isNull()).isTrue();
        assertThat(api.openOfferCount(other)).isZero();

        // The assigned driver learns about the passenger's cancellation; the other driver does not.
        assertStatus(api.call(passenger, "POST", "/api/rides/" + rideId + "/cancel", "{\"reason\":\"Plans changed\"}"), 200);
        JsonNode cancelled = winnerSocket.next(RIDES, ride -> hasStatus(ride, rideId, "CANCELLED"));
        assertThat(cancelled.get("cancellation").get("cancelledBy").asString()).isEqualTo("PASSENGER");
        otherSocket.assertNothingReceived(RIDES);
    }

    @Test
    void unauthenticatedOrUnauthorisedFramesAreRejectedAndTheSocketClosed() throws Exception {
        Connection anonymous = stomp.connectRaw(null);
        assertThat(anonymous.errorFrame().get("code").asString()).isEqualTo("UNAUTHENTICATED");
        anonymous.awaitClosed();

        Connection forged = stomp.connectRaw("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl");
        assertThat(forged.errorFrame().get("code").asString()).isEqualTo("INVALID_TOKEN");
        forged.awaitClosed();

        Actor passenger = fixtures.passenger();
        Connection foreignOrigin = stomp.connectFromOrigin(passenger, FOREIGN_ORIGIN);
        foreignOrigin.awaitClosed();
        assertThat(foreignOrigin.isConnected()).isFalse();

        Connection adminSnooping = stomp.connect(passenger);
        adminSnooping.subscribeWithoutWaiting(ADMIN_ACTIVITY);
        JsonNode refused = adminSnooping.errorFrame();
        assertThat(refused.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(refused.get("destination").asString()).isEqualTo(ADMIN_ACTIVITY);
        adminSnooping.awaitClosed();

        // Another session's resolved user queue is not addressable directly.
        Connection queueSnooping = stomp.connect(passenger);
        queueSnooping.subscribeWithoutWaiting("/queue/rides-user" + UUID.randomUUID());
        assertThat(queueSnooping.errorFrame().get("code").asString()).isEqualTo("FORBIDDEN");
        queueSnooping.awaitClosed();

        // Passengers cannot inject locations; nobody can send straight to a broker destination.
        Connection spoofing = stomp.connect(passenger);
        spoofing.send(DRIVER_LOCATION, location(HITECH_CITY, clock.instant()));
        assertThat(spoofing.errorFrame().get("code").asString()).isEqualTo("FORBIDDEN");
        spoofing.awaitClosed();
        Connection injecting = stomp.connect(passenger);
        injecting.send(ADMIN_ACTIVITY, Map.of("rideId", UUID.randomUUID().toString()));
        assertThat(injecting.errorFrame().get("code").asString()).isEqualTo("FORBIDDEN");
        injecting.awaitClosed();

        // Admins may watch the activity feed.
        Actor admin = fixtures.admin();
        Connection adminSocket = subscribed(admin, ADMIN_ACTIVITY);
        UUID rideId = api.book(fixtures.passenger(), HITECH_CITY, HUSSAIN_SAGAR);
        JsonNode activity = adminSocket.next(ADMIN_ACTIVITY, message -> isRide(message, rideId));
        assertThat(activity.get("status").asString()).isIn("REQUESTED", "MATCHING");
        assertThat(activity.has("passengerId")).isFalse();
    }

    @Test
    void theHandshakeOriginCheckIgnoresForwardingHeadersFromUntrustedClients() throws Exception {
        Actor passenger = fixtures.passenger();

        // Forwarding headers naming the foreign site would make the handshake look same-origin if they were trusted.
        Connection spoofed = stomp.connectFromOrigin(passenger, FOREIGN_ORIGIN, Map.of(
                "X-Forwarded-Proto", "https", "X-Forwarded-Host", "evil.example", "X-Forwarded-Port", "443"));
        spoofed.awaitClosed();
        assertThat(spoofed.isConnected()).isFalse();

        Connection frontend = stomp.connectFromOrigin(passenger, FRONTEND_ORIGIN);
        frontend.awaitConnected();
        assertThat(frontend.isConnected()).isTrue();
    }

    @Test
    void invalidLocationMessagesAreAnsweredWithoutClosingTheStreamAndFloodsAreThrottled() throws Exception {
        GeoPoint start = offset(HITECH_CITY, 300, 0);
        Actor driver = onlineDriver(start);
        Connection socket = subscribed(driver, ERRORS);

        socket.send(DRIVER_LOCATION, Map.of("location", Map.of("lat", 95.0, "lng", 78.0),
                "recordedAt", clock.instant().toString()));
        JsonNode invalid = socket.next(ERRORS);
        assertThat(invalid.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(invalid.get("fieldErrors").get(0).get("field").asString()).isEqualTo("location.lat");

        socket.send(DRIVER_LOCATION, location(start, clock.instant().minus(Duration.ofMinutes(10))));
        assertThat(socket.next(ERRORS).get("code").asString()).isEqualTo("STALE_LOCATION");

        // Still connected: a valid fix is stored.
        clock.advance(Duration.ofSeconds(1));
        GeoPoint first = offset(HITECH_CITY, 250, 0);
        socket.send(DRIVER_LOCATION, location(first, clock.instant()));
        await().atMost(StompTestClient.TIMEOUT).untilAsserted(() -> assertThat(
                driverLocations.find(driver.id()).orElseThrow().point().lat()).isCloseTo(first.lat(), within(1e-7)));

        // A second fix within the same second is dropped (and counted), so the stored position does not move.
        double droppedBefore = droppedLocations();
        socket.send(DRIVER_LOCATION, location(offset(HITECH_CITY, 100, 0), clock.instant()));
        await().atMost(StompTestClient.TIMEOUT).until(() -> droppedLocations() == droppedBefore + 1);
        assertThat(driverLocations.find(driver.id()).orElseThrow().point().lat()).isCloseTo(first.lat(), within(1e-7));
        assertThat(socket.isConnected()).isTrue();
        socket.assertNothingReceived(ERRORS);
    }

    @Test
    void silentAvailableDriversAreTakenOfflineAndToldButDriversOnATripAreNot() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor onTrip = onlineDriver(offset(HITECH_CITY, 300, 0));
        Connection onTripSocket = subscribed(onTrip, RIDE_OFFERS, PRESENCE);
        UUID rideId = assignedRide(passenger, onTrip, onTripSocket);

        Actor silent = onlineDriver(offset(HITECH_CITY, 900, 0));
        Actor active = onlineDriver(offset(HITECH_CITY, 1_200, 0));
        Connection silentSocket = subscribed(silent, PRESENCE);

        clock.advance(PRESENCE_TIMEOUT.plusSeconds(1));
        assertStatus(api.reportLocation(active, offset(HITECH_CITY, 1_150, 0), clock.instant()), 202);

        assertThat(presenceSweeper.sweep()).isEqualTo(1);

        JsonNode notice = silentSocket.next(PRESENCE);
        assertThat(notice.get("availability").asString()).isEqualTo("OFFLINE");
        assertThat(notice.get("reason").asString()).isEqualTo("LOCATION_TIMEOUT");
        assertThat(availability(silent)).isEqualTo("OFFLINE");
        assertThat(availability(active)).isEqualTo("AVAILABLE");
        assertThat(availability(onTrip)).isEqualTo("ON_TRIP");
        onTripSocket.assertNothingReceived(PRESENCE);

        // The passenger's snapshot shows the silent driver's position as stale, without an ETA.
        DocumentContext tracking = json(api.call(passenger, "GET", "/api/rides/" + rideId + "/tracking", null));
        assertThat(tracking.read("$.stale", Boolean.class)).isTrue();
        assertThat(tracking.read("$.driverLocation.point.lat", Double.class)).isNotNull();
        assertThat(tracking.read("$.eta", Object.class)).isNull();
    }

    @Test
    void socketsAreClosedWhenTheirAccessTokenExpires() throws Exception {
        Actor passenger = fixtures.passenger();
        Connection socket = subscribed(passenger, RIDES);

        clock.advance(ACCESS_TOKEN_TTL);
        assertThat(sessionRegistry.closeExpiredSessions()).isPositive();

        socket.awaitClosed();
        assertThat(socket.isConnected()).isFalse();
    }

    private double droppedLocations() {
        return meters.get("rideflow.ws.location.dropped").counter().count();
    }

    private String availability(Actor driver) {
        return jdbc.queryForObject("SELECT availability FROM drivers WHERE id = ?", String.class, driver.id());
    }
}
