package com.rideflow.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.KafkaTestSupport;
import com.rideflow.support.MutableClock;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideJourneys;
import com.rideflow.support.RideTestConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI Trip Intelligence end to end: a completed ride's {@code ride.completed} event reaches the trip-analysis
 * consumer through Kafka, which calls the local provider (a WireMock server playing Ollama here; the real model
 * run is documented in docs/ai.md) and stores the validated analysis. Also shows that AI failures never touch
 * the ride: completion and payment go through, and the analysis is marked FAILED until it is regenerated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RideTestConfig.class)
class AITripInsightsIT extends IntegrationTestContainers {

    private static final WireMockServer OLLAMA = new WireMockServer(options().dynamicPort());
    private static final String CHAT = "/api/chat";
    private static final String MODEL = "stand-in-model";
    private static final Duration AWAIT = Duration.ofSeconds(20);
    private static final int QUESTION_PRIORITY = 1;
    private static final int ANALYSIS_PRIORITY = 5;

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

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private MutableClock clock;
    @Autowired
    private KafkaTestSupport kafka;
    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private RideApi api;

    /** Contains no numbers, so it is grounded whatever the trip's facts are. */
    private final String insights = json.writeValueAsString(Map.of(
            "summary", "Your fare followed the rate card for the distance and time driven.",
            "fareExplanation", "The final fare adds the base fare, the distance and time charges and the booking fee.",
            "observations", List.of(Map.of("type", "FARE", "text", "The booking fee is part of every fare.")),
            "recommendations", List.of(),
            "factKeysUsed", List.of("fare.final.total", "fare.final.bookingFee")));

    private final String answer = json.writeValueAsString(Map.of(
            "answerable", true,
            "answer", "The final fare adds the base fare, the distance and time charges and the booking fee.",
            "factKeysUsed", List.of("fare.final.total")));

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
        OLLAMA.resetAll();
        stubAnalysis(200, insights);
        OLLAMA.stubFor(post(CHAT).atPriority(QUESTION_PRIORITY).withRequestBody(containing("<question>"))
                .willReturn(ok(answer)));
    }

    private void stubAnalysis(int status, String content) {
        OLLAMA.stubFor(post(CHAT).atPriority(ANALYSIS_PRIORITY)
                .willReturn(status == 200 ? ok(content) : aResponse().withStatus(status)));
    }

    private ResponseDefinitionBuilder ok(String content) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(json.writeValueAsString(Map.of(
                "model", MODEL, "done", true, "prompt_eval_count", 300, "eval_count", 80,
                "message", Map.of("role", "assistant", "content", content))));
    }

    private MvcResult analysis(Actor user, UUID rideId) throws Exception {
        return api.call(user, "GET", "/api/trips/" + rideId + "/ai-analysis", null);
    }

    private DocumentContext awaitAnalysis(Actor passenger, UUID rideId, String status) {
        await().atMost(AWAIT).until(() -> status.equals(JsonPath.read(body(analysis(passenger, rideId)), "$.status")));
        try {
            return JsonPath.parse(body(analysis(passenger, rideId)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void completedRidesAreAnalysedAndOnlyThePassengerCanSeeIt() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor driver = RideJourneys.onlineDriver(fixtures, api);
        UUID pending = RideJourneys.offeredRide(api, passenger, driver, PaymentMethod.CASH);
        assertError(analysis(passenger, pending), 409, "RIDE_NOT_COMPLETED");
        assertStatus(api.call(passenger, "POST", "/api/rides/" + pending + "/cancel", null), 200);

        UUID rideId = RideJourneys.completedRide(api, clock, passenger, RideJourneys.onlineDriver(fixtures, api),
                PaymentMethod.CASH);

        DocumentContext analysis = awaitAnalysis(passenger, rideId, "COMPLETED");
        assertThat(analysis.read("$.insights.summary", String.class)).startsWith("Your fare followed the rate card");
        assertThat(analysis.read("$.provider", String.class)).isEqualTo("local");
        assertThat(analysis.read("$.model", String.class)).isEqualTo(MODEL);
        assertThat(analysis.read("$.promptVersion", String.class)).isEqualTo("trip-analysis/v1");
        List<Map<String, Object>> observations = analysis.read("$.observations");
        assertThat(observations).allSatisfy(observation -> assertThat(observation).containsKeys("key", "text"));
        assertError(analysis(fixtures.passenger(), rideId), 404, "RIDE_NOT_FOUND");
        assertThat(analysis(driver, rideId).getResponse().getStatus()).isEqualTo(403);

        // The stored input: trip numbers only, nothing that identifies a person or a place.
        String facts = jdbc.queryForObject("SELECT facts::text FROM trip_analyses WHERE ride_id = ?", String.class, rideId);
        Map<String, Object> values = JsonPath.read(facts, "$.values");
        assertThat(values).containsKeys("fare.final.total", "distance.actualKm");
        assertThat(values.keySet()).noneMatch(key -> key.matches("(?i).*(address|name|email|phone|lat|lng).*"));
        assertThat(facts).doesNotContain("Pickup", "Dropoff", "@example.com");
    }

    @Test
    void questionsAreAnsweredFromTheTripAndKept() throws Exception {
        Actor passenger = fixtures.passenger();
        UUID rideId = RideJourneys.completedRide(api, clock, passenger, RideJourneys.onlineDriver(fixtures, api),
                PaymentMethod.CASH);
        String questions = "/api/trips/" + rideId + "/ai-analysis/questions";

        MvcResult asked = api.call(passenger, "POST", questions, "{\"question\":\"How is my fare calculated?\"}");
        assertStatus(asked, 200);
        assertThat(JsonPath.<Boolean>read(body(asked), "$.answerable")).isTrue();
        assertError(api.call(passenger, "POST", questions, "{\"question\":\"  \"}"), 400, "VALIDATION_FAILED");

        List<String> history = JsonPath.read(body(api.call(passenger, "GET", questions, null)), "$[*].question");
        assertThat(history).containsExactly("How is my fare calculated?");
    }

    @Test
    void aFailingProviderNeverAffectsTheRideAndTheAnalysisCanBeRegenerated() throws Exception {
        stubAnalysis(500, null);
        Actor passenger = fixtures.passenger();

        UUID rideId = RideJourneys.completedRide(api, clock, passenger, RideJourneys.onlineDriver(fixtures, api),
                PaymentMethod.CASH);

        // The ride completed and was paid regardless; only the AI part failed.
        DocumentContext failed = awaitAnalysis(passenger, rideId, "FAILED");
        assertThat(failed.read("$.failureCode", String.class)).isEqualTo("PROVIDER_ERROR");
        assertThat(failed.read("$.insights", Object.class)).isNull();
        kafka.awaitIdle();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE ride_id = ?", String.class, rideId))
                .isEqualTo("CAPTURED");

        OLLAMA.resetAll();
        stubAnalysis(200, insights);
        MvcResult regenerated = api.call(passenger, "POST", "/api/trips/" + rideId + "/ai-analysis/regenerate", null);
        assertStatus(regenerated, 202);
        assertThat(awaitAnalysis(passenger, rideId, "COMPLETED").read("$.failureCode", Object.class)).isNull();
        assertError(api.call(passenger, "POST", "/api/trips/" + rideId + "/ai-analysis/regenerate", null), 409,
                "AI_ANALYSIS_NOT_REGENERABLE");
    }

    @Test
    void aQuestionTheProviderCannotAnswerIsA503AndIsRecorded() throws Exception {
        Actor passenger = fixtures.passenger();
        UUID rideId = RideJourneys.completedRide(api, clock, passenger, RideJourneys.onlineDriver(fixtures, api),
                PaymentMethod.CASH);
        OLLAMA.stubFor(post(CHAT).atPriority(QUESTION_PRIORITY).withRequestBody(containing("<question>"))
                .willReturn(aResponse().withStatus(503)));
        String questions = "/api/trips/" + rideId + "/ai-analysis/questions";

        assertError(api.call(passenger, "POST", questions, "{\"question\":\"Why this price?\"}"), 503, "AI_UNAVAILABLE");

        List<String> statuses = JsonPath.read(body(api.call(passenger, "GET", questions, null)), "$[*].status");
        assertThat(statuses).containsExactly("FAILED");
    }

    private static void assertStatus(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
    }

    private static void assertError(MvcResult result, int status, String code) throws Exception {
        assertStatus(result, status);
        assertThat(JsonPath.<String>read(body(result), "$.code")).isEqualTo(code);
    }
}
