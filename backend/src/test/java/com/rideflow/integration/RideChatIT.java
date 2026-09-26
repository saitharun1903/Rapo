package com.rideflow.integration;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.HUSSAIN_SAGAR;
import static com.rideflow.support.GeoTestPoints.offset;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.MutableClock;
import com.rideflow.support.RealtimeTestConfig;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import com.rideflow.support.StompTestClient;
import com.rideflow.support.StompTestClient.Connection;
import com.rideflow.support.SubscriptionProbe;
import java.time.Duration;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * In-ride chat over HTTP and a real WebSocket (docs/feature-spec.md section 3), and the ride view a driver gets
 * before accepting (section 4.8): who can read and write, when chat is open, what a re-dispatched driver sees.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RideTestConfig.class, RealtimeTestConfig.class})
class RideChatIT extends IntegrationTestContainers {

    private static final String RIDE_MESSAGES = "/user/queue/ride-messages";
    private static final String RIDE_OFFERS = "/user/queue/ride-offers";
    private static final Duration AWAIT = Duration.ofSeconds(15);

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

    private static void assertError(MvcResult result, int status, String code) throws Exception {
        assertStatus(result, status);
        assertThat(json(result).read("$.code", String.class)).isEqualTo(code);
    }

    private Actor onlineDriver(GeoPoint at) throws Exception {
        Actor driver = fixtures.verifiedDriver(VehicleCategory.ECONOMY);
        assertStatus(api.goOnline(driver, at), 200);
        return driver;
    }

    private MvcResult send(Actor actor, UUID rideId, String text) throws Exception {
        return api.call(actor, "POST", messages(rideId), "{\"body\":" + quoted(text) + "}");
    }

    private static String messages(UUID rideId) {
        return "/api/rides/" + rideId + "/messages";
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private List<String> bodies(Actor actor, UUID rideId) throws Exception {
        MvcResult result = api.call(actor, "GET", messages(rideId), null);
        assertStatus(result, 200);
        return json(result).read("$[*].body");
    }

    @Test
    void messagesReachOnlyTheParticipantsWhileADriverIsOnTheRide() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor bystander = fixtures.passenger();
        Actor driver = onlineDriver(offset(HITECH_CITY, 300, 0));
        Connection driverSocket = stomp.connect(driver);
        driverSocket.subscribe(RIDE_OFFERS);
        driverSocket.subscribe(RIDE_MESSAGES);
        Connection passengerSocket = stomp.connect(passenger);
        passengerSocket.subscribe(RIDE_MESSAGES);
        Connection bystanderSocket = stomp.connect(bystander);
        bystanderSocket.subscribe(RIDE_MESSAGES);

        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        driverSocket.next(RIDE_OFFERS, offer -> rideId.toString().equals(offer.get("rideId").asString()));

        // No driver yet: nobody to talk to.
        assertError(send(passenger, rideId, "Hello?"), 409, "CHAT_CLOSED");
        // A driver who has only been offered the ride is not a participant.
        assertError(api.call(driver, "GET", messages(rideId), null), 404, "RIDE_NOT_FOUND");

        assertStatus(api.call(driver, "POST", "/api/rides/" + rideId + "/accept", null), 200);

        MvcResult sent = send(passenger, rideId, "  I'm at the main gate  ");
        assertStatus(sent, 201);
        assertThat(json(sent).read("$.body", String.class)).isEqualTo("I'm at the main gate");
        assertThat(json(sent).read("$.senderRole", String.class)).isEqualTo("PASSENGER");

        // Pushed to both participants (the sender's other devices included), through the outbox and Kafka.
        JsonNode atDriver = driverSocket.next(RIDE_MESSAGES);
        assertThat(atDriver.get("body").asString()).isEqualTo("I'm at the main gate");
        assertThat(atDriver.get("rideId").asString()).isEqualTo(rideId.toString());
        assertThat(passengerSocket.next(RIDE_MESSAGES).get("senderId").asString()).isEqualTo(passenger.id().toString());

        clock.advance(Duration.ofSeconds(5));
        assertStatus(send(driver, rideId, "On my way"), 201);
        assertThat(passengerSocket.next(RIDE_MESSAGES).get("senderRole").asString()).isEqualTo("DRIVER");

        assertThat(bodies(passenger, rideId)).containsExactly("I'm at the main gate", "On my way");
        assertThat(bodies(driver, rideId)).containsExactly("I'm at the main gate", "On my way");

        // Strangers can neither read nor write, and hear nothing.
        assertError(api.call(bystander, "GET", messages(rideId), null), 404, "RIDE_NOT_FOUND");
        assertError(send(bystander, rideId, "Hi"), 404, "RIDE_NOT_FOUND");
        bystanderSocket.assertNothingReceived(RIDE_MESSAGES);

        // Validation: blank and overlong messages are refused.
        assertError(send(passenger, rideId, "   "), 400, "VALIDATION_FAILED");
        assertError(send(passenger, rideId, "x".repeat(501)), 400, "VALIDATION_FAILED");

        // Once the ride is over the conversation is closed, but still readable by its participants.
        assertStatus(api.call(passenger, "POST", "/api/rides/" + rideId + "/cancel", "{\"reason\":\"Plans changed\"}"), 200);
        assertError(send(passenger, rideId, "Sorry"), 409, "CHAT_CLOSED");
        assertThat(bodies(passenger, rideId)).hasSize(2);
    }

    @Test
    void aDriverSeesWhoBookedOnlyAfterAccepting() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor first = onlineDriver(offset(HITECH_CITY, 200, 0));
        Actor second = onlineDriver(offset(HITECH_CITY, 400, 0));
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> api.openOfferCount(first) == 1 && api.openOfferCount(second) == 1);

        DocumentContext offered = json(api.call(first, "GET", "/api/rides/" + rideId, null));
        assertThat(offered.read("$.status", String.class)).isEqualTo("MATCHING");
        assertThat(offered.read("$.passenger", Map.class)).isNull();
        assertThat(offered.read("$.pickup.address", String.class)).isNotBlank();

        assertStatus(api.call(first, "POST", "/api/rides/" + rideId + "/accept", null), 200);

        DocumentContext accepted = json(api.call(first, "GET", "/api/rides/" + rideId, null));
        assertThat(accepted.read("$.passenger.id", String.class)).isEqualTo(passenger.id().toString());
        assertThat(accepted.read("$.passenger.fullName", String.class)).isNotBlank();
        // The other driver's offer was withdrawn: the ride is no longer theirs to see.
        assertError(api.call(second, "GET", "/api/rides/" + rideId, null), 404, "RIDE_NOT_FOUND");
        // The passenger's own view is unchanged.
        assertThat(json(api.call(passenger, "GET", "/api/rides/" + rideId, null)).read("$.passenger.id", String.class))
                .isEqualTo(passenger.id().toString());
    }

    @Test
    void aReDispatchedDriverDoesNotSeeTheEarlierConversation() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor first = onlineDriver(offset(HITECH_CITY, 200, 0));
        UUID rideId = api.book(passenger, HITECH_CITY, HUSSAIN_SAGAR);
        await().atMost(AWAIT).until(() -> api.openOfferCount(first) == 1);
        assertStatus(api.call(first, "POST", "/api/rides/" + rideId + "/accept", null), 200);
        assertStatus(send(passenger, rideId, "Gate 2, blue building"), 201);
        clock.advance(Duration.ofSeconds(1));
        assertStatus(send(first, rideId, "Flat tyre, sorry"), 201);

        Actor second = onlineDriver(offset(HITECH_CITY, 400, 0));
        assertStatus(api.call(first, "POST", "/api/rides/" + rideId + "/cancel", "{\"reason\":\"Flat tyre\"}"), 200);
        await().atMost(AWAIT).until(() -> api.openOfferCount(second) == 1);
        clock.advance(Duration.ofSeconds(1));
        assertStatus(api.call(second, "POST", "/api/rides/" + rideId + "/accept", null), 200);

        assertThat(bodies(second, rideId)).isEmpty();
        assertError(api.call(first, "GET", messages(rideId), null), 404, "RIDE_NOT_FOUND");
        assertThat(bodies(passenger, rideId)).containsExactly("Gate 2, blue building", "Flat tyre, sorry");

        clock.advance(Duration.ofSeconds(1));
        assertStatus(send(passenger, rideId, "I'm at gate 2"), 201);
        assertThat(bodies(second, rideId)).containsExactly("I'm at gate 2");
    }
}
