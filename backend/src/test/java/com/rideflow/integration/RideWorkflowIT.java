package com.rideflow.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.HUSSAIN_SAGAR;
import static com.rideflow.support.GeoTestPoints.offset;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.MutableClock;
import com.rideflow.support.PrometheusScrape;
import com.rideflow.support.RealtimeTestConfig;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import com.rideflow.support.StompTestClient;
import com.rideflow.support.StompTestClient.Connection;
import com.rideflow.support.SubscriptionProbe;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole ride workflow in one pass, the way the two apps drive it: HTTP for commands, one STOMP socket per
 * participant for what happens next, and Kafka between every step. The passenger books; matching offers the
 * ride to the driver over the socket; the driver accepts, streams GPS over the socket, arrives, starts and
 * completes; the passenger hears each step and each position; then payment, notifications, the AI analysis
 * (a WireMock server playing Ollama), ratings, earnings and the admin view all agree on the same ride, and the
 * Prometheus scrape counted it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RideTestConfig.class, RealtimeTestConfig.class})
class RideWorkflowIT extends IntegrationTestContainers {

    private static final String RIDES = "/user/queue/rides";
    private static final String RIDE_LOCATION = "/user/queue/ride-location";
    private static final String RIDE_OFFERS = "/user/queue/ride-offers";
    private static final String NOTIFICATIONS = "/user/queue/notifications";
    private static final String DRIVER_LOCATION = "/app/drivers/location";
    private static final WireMockServer OLLAMA = new WireMockServer(options().dynamicPort());
    private static final String MODEL = "stand-in-model";
    private static final Duration AWAIT = Duration.ofSeconds(20);
    private static final double DRIVER_START_METERS = 300;
    private static final double AT_PICKUP_METERS = 40;
    /** Shorter than the access token lifetime (the sockets close when it ends), which the test clock governs. */
    private static final Duration TRIP = Duration.ofMinutes(10);
    /** The server accepts one location report per second from a socket. */
    private static final Duration BETWEEN_REPORTS = Duration.ofSeconds(1);
    private static final double COORDINATE_TOLERANCE = 1e-9;

    static {
        OLLAMA.start();
    }

    @DynamicPropertySource
    static void localProvider(DynamicPropertyRegistry registry) {
        registry.add("rideflow.ai.provider", () -> "local");
        registry.add("rideflow.ai.local.base-url", OLLAMA::baseUrl);
        registry.add("rideflow.ai.local.model", () -> MODEL);
        registry.add("rideflow.ai.local.timeout", () -> "5s");
    }

    @LocalServerPort
    private int port;
    @LocalManagementPort
    private int managementPort;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private MutableClock clock;
    @Autowired
    private SubscriptionProbe probe;
    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private RideApi api;
    private StompTestClient stomp;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
        stomp = new StompTestClient(port, probe);
        OLLAMA.resetAll();
        // No numbers in the text, so it is grounded whatever the trip's facts are.
        String insights = json.writeValueAsString(Map.of(
                "summary", "Your fare followed the rate card for the distance and time driven.",
                "fareExplanation", "The final fare adds the base fare, the distance and time charges and the booking fee.",
                "observations", List.of(),
                "recommendations", List.of(),
                "factKeysUsed", List.of("fare.final.total")));
        OLLAMA.stubFor(post("/api/chat").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(json.writeValueAsString(Map.of("model", MODEL, "done", true,
                        "prompt_eval_count", 300, "eval_count", 80,
                        "message", Map.of("role", "assistant", "content", insights))))));
    }

    @AfterEach
    void tearDown() {
        stomp.close();
    }

    @Test
    void aRideGoesFromRequestToPaymentAndInsightsOverHttpWebSocketAndKafka() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        Actor admin = fixtures.admin();
        assertStatus(api.goOnline(driver, offset(HITECH_CITY, DRIVER_START_METERS, 0)), 200);
        Connection driverSocket = subscribed(driver, RIDE_OFFERS, RIDES);
        Connection passengerSocket = subscribed(passenger, RIDES, RIDE_LOCATION, NOTIFICATIONS);
        Instant booked = clock.instant();
        PrometheusScrape before = PrometheusScrape.of(managementPort);

        // Booking goes out as ride.requested; the matching consumer offers the ride over the driver's socket.
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR, PaymentMethod.CARD);
        String ride = "/api/rides/" + rideId;
        JsonNode offer = driverSocket.next(RIDE_OFFERS, message -> isRide(message.get("rideId"), rideId));
        assertThat(offer.get("type").asString()).isEqualTo("OFFER");

        assertStatus(api.call(driver, "POST", ride + "/accept", null), 200);
        passengerSocket.next(RIDES, update -> hasStatus(update, rideId, "DRIVER_ASSIGNED"));
        assertStatus(api.call(driver, "POST", ride + "/en-route", null), 200);
        passengerSocket.next(RIDES, update -> hasStatus(update, rideId, "DRIVER_ARRIVING"));

        // GPS streamed over the driver's socket reaches the passenger, and the arrival geofence uses it.
        streamLocation(driverSocket, passengerSocket, offset(HITECH_CITY, AT_PICKUP_METERS, 0));
        assertStatus(api.call(driver, "POST", ride + "/arrive", null), 200);
        passengerSocket.next(RIDES, update -> hasStatus(update, rideId, "DRIVER_ARRIVED"));
        assertStatus(api.call(driver, "POST", ride + "/start", null), 200);
        passengerSocket.next(RIDES, update -> hasStatus(update, rideId, "IN_PROGRESS"));

        clock.advance(TRIP);
        streamLocation(driverSocket, passengerSocket, HUSSAIN_SAGAR);
        assertStatus(api.call(driver, "POST", ride + "/complete", null), 200);
        JsonNode completed = passengerSocket.next(RIDES, update -> hasStatus(update, rideId, "COMPLETED"));
        BigDecimal fare = new BigDecimal(completed.get("actual").get("fare").get("amount").asString());
        assertThat(fare).isPositive();
        driverSocket.next(RIDES, update -> hasStatus(update, rideId, "COMPLETED"));

        // The payments consumer captured the fare; the passenger was told over the socket.
        await().atMost(AWAIT).until(() -> "CAPTURED".equals(
                json(api.call(passenger, "GET", ride, null)).read("$.payment.status", String.class)));
        DocumentContext paid = json(api.call(passenger, "GET", ride, null));
        assertThat(new BigDecimal(paid.read("$.payment.amount.amount", String.class))).isEqualByComparingTo(fare);
        assertThat(pushedNotifications(passengerSocket, "PAYMENT_RECEIVED"))
                .contains("DRIVER_ACCEPTED", "TRIP_COMPLETED", "PAYMENT_RECEIVED");

        // The trip-analysis consumer stored grounded insights for the passenger.
        await().atMost(AWAIT).until(() -> "COMPLETED".equals(JsonPath.read(
                body(api.call(passenger, "GET", "/api/trips/" + rideId + "/ai-analysis", null)), "$.status")));
        assertThat(OLLAMA.getAllServeEvents()).isNotEmpty();

        // Both sides rate; the driver's earnings and the admin console show this ride and its money.
        assertStatus(api.call(passenger, "POST", ride + "/rating", "{\"score\":5}"), 201);
        assertStatus(api.call(driver, "POST", ride + "/rating", "{\"score\":5}"), 201);
        BigDecimal driverShare = jdbc.queryForObject(
                "SELECT driver_earnings FROM payments WHERE ride_id = ?", BigDecimal.class, rideId);
        String window = "from=" + booked.minus(Duration.ofDays(1)) + "&to=" + clock.instant().plus(Duration.ofDays(1));
        DocumentContext earnings = json(api.call(driver, "GET", "/api/drivers/me/earnings?" + window, null));
        assertThat(new BigDecimal(earnings.read("$.total.amount", String.class))).isEqualByComparingTo(driverShare);
        assertThat(earnings.read("$.tripCount", Integer.class)).isEqualTo(1);
        DocumentContext detail = json(api.call(admin, "GET", "/api/admin/rides/" + rideId, null));
        assertThat(detail.read("$.ride.status", String.class)).isEqualTo("COMPLETED");
        List<String> timeline = detail.read("$.timeline[*].to");
        assertThat(timeline).containsExactly("REQUESTED", "MATCHING", "DRIVER_ASSIGNED", "DRIVER_ARRIVING",
                "DRIVER_ARRIVED", "IN_PROGRESS", "COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class))
                .isZero();

        // The series the Grafana dashboards query moved by exactly this ride.
        PrometheusScrape after = PrometheusScrape.of(managementPort);
        for (String status : timeline) {
            assertThat(increase(before, after, "rideflow_rides_total", "event", status.toLowerCase(Locale.ROOT)))
                    .as(status).isEqualTo(1);
        }
        assertThat(increase(before, after, "rideflow_offers_total", "outcome", "created")).isEqualTo(1);
        assertThat(increase(before, after, "rideflow_offers_total", "outcome", "accepted")).isEqualTo(1);
        assertThat(increase(before, after, "rideflow_matching_duration_seconds_bucket", "le", "+Inf")).isEqualTo(1);
        assertThat(increase(before, after, "rideflow_location_updates_total", "result", "accepted")).isEqualTo(2);
        // Redis command timings come from Lettuce's observations (the Real-time & cache dashboard).
        assertThat(increase(before, after, "lettuce_seconds_count", "db_system", "redis")).isPositive();
    }

    private static double increase(PrometheusScrape before, PrometheusScrape after, String name, String label,
                                   String value) {
        return after.value(name, label, value) - before.value(name, label, value);
    }

    private Connection subscribed(Actor actor, String... destinations) throws Exception {
        Connection connection = stomp.connect(actor);
        for (String destination : destinations) {
            connection.subscribe(destination);
        }
        return connection;
    }

    /** Sends one GPS fix over the driver's socket and waits until it has reached the passenger's. */
    private void streamLocation(Connection driverSocket, Connection passengerSocket, GeoPoint point)
            throws InterruptedException {
        clock.advance(BETWEEN_REPORTS);
        driverSocket.send(DRIVER_LOCATION, Map.of("location", Map.of("lat", point.lat(), "lng", point.lng()),
                "recordedAt", clock.instant().toString()));
        JsonNode pushed = passengerSocket.next(RIDE_LOCATION,
                message -> Math.abs(message.get("location").get("lat").asDouble() - point.lat()) < COORDINATE_TOLERANCE);
        assertThat(pushed.get("location").get("lng").asDouble()).isCloseTo(point.lng(), within(COORDINATE_TOLERANCE));
    }

    /** Collects the notification types pushed to {@code socket}, up to and including {@code last}. */
    private static List<String> pushedNotifications(Connection socket, String last) throws InterruptedException {
        List<String> types = new ArrayList<>();
        while (!types.contains(last)) {
            types.add(socket.next(NOTIFICATIONS).get("type").asString());
        }
        return types;
    }

    private static boolean isRide(JsonNode id, UUID rideId) {
        return id != null && rideId.toString().equals(id.asString());
    }

    private static boolean hasStatus(JsonNode ride, UUID rideId, String status) {
        return isRide(ride.get("id"), rideId) && status.equals(ride.get("status").asString());
    }

    private static DocumentContext json(MvcResult result) throws Exception {
        return JsonPath.parse(body(result));
    }

    private static void assertStatus(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
    }
}
