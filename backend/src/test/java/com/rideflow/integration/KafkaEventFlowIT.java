package com.rideflow.integration;

import static com.rideflow.support.RideApi.body;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.kafka.event.EventDecodingException;
import com.rideflow.kafka.event.EventTopic;
import com.rideflow.kafka.event.KafkaNames;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.KafkaTestSupport;
import com.rideflow.support.MutableClock;
import com.rideflow.support.RideApi;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideJourneys;
import com.rideflow.support.RideTestConfig;
import com.rideflow.support.TopicRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The event pipeline against a real Kafka broker: domain events leave through the outbox, travel over Kafka
 * topics (observed here with an independent consumer), and drive matching, payments and notifications.
 * Also covers what happens to records that cannot be processed (dead-letter topics) and to records delivered
 * twice (idempotent consumers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RideTestConfig.class)
class KafkaEventFlowIT extends IntegrationTestContainers {

    private static final Configuration LENIENT = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.20");
    /** Initial retry interval 1 s, multiplier 2, 3 retries (application.yml): 1 + 2 + 4 seconds. */
    private static final Duration RETRY_BACKOFF_TOTAL = Duration.ofSeconds(7);
    /** Consumer groups subscribed to ride.completed; each handles, and dead-letters, its own copy of a record. */
    private static final List<String> RIDE_COMPLETED_GROUPS = List.of("payments", "notifications", "trip-analysis");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private MutableClock clock;
    @Autowired
    private KafkaTestSupport kafka;
    @Autowired
    private KafkaNames names;
    @Autowired
    private ConsumerFactory<Object, Object> consumerFactory;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MeterRegistry meters;

    private RideApi api;

    @BeforeEach
    void setUp() {
        fixtures.reset();
        api = new RideApi(mvc);
    }

    @Test
    void fullRideLifecycleFlowsThroughKafka() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor driver = onlineDriver();
        List<String> topics = List.of(EventTopic.RIDE_REQUESTED, EventTopic.RIDE_MATCHING, EventTopic.RIDE_DRIVER_ASSIGNED,
                        EventTopic.RIDE_ACCEPTED, EventTopic.RIDE_DRIVER_ARRIVING, EventTopic.RIDE_DRIVER_ARRIVED,
                        EventTopic.RIDE_STARTED, EventTopic.RIDE_COMPLETED, EventTopic.PAYMENT_CREATED)
                .stream().map(names::topic).toList();

        try (TopicRecorder recorder = new TopicRecorder(consumerFactory, topics)) {
            UUID rideId = completedRide(passenger, driver, PaymentMethod.CASH);

            // Every step of the ride went through its Kafka topic, keyed so a ride's events stay ordered.
            List<ConsumerRecord<Object, Object>> events = recorder.awaitRecords(record -> isAbout(record, rideId),
                    received -> eventTypes(received).size() == topics.size());
            assertThat(eventTypes(events)).containsExactlyInAnyOrder("ride.requested", "ride.matching",
                    "ride.driver.assigned", "ride.accepted", "ride.driver.arriving", "ride.driver.arrived",
                    "ride.started", "ride.completed", "payment.created");
            List<String> statusOrder = events.stream()
                    .filter(record -> envelope(record).read("$.aggregateVersion") != null)
                    .sorted(Comparator.comparing(record -> envelope(record).read("$.aggregateVersion", Long.class)))
                    .map(record -> envelope(record).read("$.eventType", String.class))
                    .toList();
            assertThat(statusOrder).containsExactly("ride.requested", "ride.matching", "ride.accepted",
                    "ride.driver.arriving", "ride.driver.arrived", "ride.started", "ride.completed");
            assertThat(events).filteredOn(record -> !record.topic().equals(names.topic(EventTopic.PAYMENT_CREATED)))
                    .allSatisfy(record -> assertThat(record.key()).isEqualTo(rideId.toString()));

            // The payments consumer settled the final fare; the ride view shows it.
            kafka.awaitIdle();
            DocumentContext ride = json(api.call(passenger, "GET", "/api/rides/" + rideId, null));
            BigDecimal fare = new BigDecimal(ride.read("$.actual.fare.amount", String.class));
            assertThat(ride.read("$.payment.status", String.class)).isEqualTo("CAPTURED");
            assertThat(ride.read("$.payment.provider", String.class)).isEqualTo("CASH");
            assertThat(new BigDecimal(ride.read("$.payment.amount.amount", String.class))).isEqualByComparingTo(fare);
            Map<String, Object> payment = jdbc.queryForMap(
                    "SELECT platform_fee, driver_earnings FROM payments WHERE ride_id = ?", rideId);
            assertThat((BigDecimal) payment.get("platform_fee"))
                    .isEqualByComparingTo(fare.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP));
            assertThat(((BigDecimal) payment.get("platform_fee")).add((BigDecimal) payment.get("driver_earnings")))
                    .isEqualByComparingTo(fare);

            // The notifications consumer told each side what happened.
            assertThat(notificationTypes(passenger)).containsExactlyInAnyOrder("DRIVER_ACCEPTED", "DRIVER_ARRIVING",
                    "DRIVER_ARRIVED", "TRIP_STARTED", "TRIP_COMPLETED", "PAYMENT_RECEIVED");
            assertThat(notificationTypes(driver)).containsExactly("EARNINGS_RECORDED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class))
                    .isZero();
        }
    }

    @Test
    void notificationsCanBeListedAndMarkedRead() throws Exception {
        Actor passenger = fixtures.passenger();
        completedRide(passenger, onlineDriver(), PaymentMethod.CASH);
        kafka.awaitIdle();

        DocumentContext unread = json(api.call(passenger, "GET", "/api/notifications?unreadOnly=true", null));
        int total = unread.read("$.totalElements", Integer.class);
        String newest = unread.read("$.content[0].id", String.class);

        assertStatus(api.call(passenger, "POST", "/api/notifications/" + newest + "/read", null), 204);
        assertThat(json(api.call(passenger, "GET", "/api/notifications?unreadOnly=true", null))
                .read("$.totalElements", Integer.class)).isEqualTo(total - 1);
        assertError(api.call(fixtures.passenger(), "POST", "/api/notifications/" + newest + "/read", null), 404,
                "NOTIFICATION_NOT_FOUND");

        assertStatus(api.call(passenger, "POST", "/api/notifications/read-all", null), 204);
        assertThat(json(api.call(passenger, "GET", "/api/notifications?unreadOnly=true", null))
                .read("$.totalElements", Integer.class)).isZero();
    }

    @Test
    void participantsRateEachOtherOnceAfterTheTrip() throws Exception {
        Actor passenger = fixtures.passenger();
        Actor driver = onlineDriver();
        UUID rideId = completedRide(passenger, driver, PaymentMethod.CASH);

        MvcResult rated = api.call(passenger, "POST", "/api/rides/" + rideId + "/rating",
                "{\"score\":4,\"comment\":\" Smooth \"}");
        assertStatus(rated, 201);
        assertThat(json(rated).read("$.comment", String.class)).isEqualTo("Smooth");
        assertError(api.call(passenger, "POST", "/api/rides/" + rideId + "/rating", "{\"score\":5}"), 409, "ALREADY_RATED");
        assertStatus(api.call(driver, "POST", "/api/rides/" + rideId + "/rating", "{\"score\":5}"), 201);
        assertError(api.call(fixtures.passenger(), "POST", "/api/rides/" + rideId + "/rating", "{\"score\":5}"), 404,
                "RIDE_NOT_FOUND");
        assertError(api.call(passenger, "POST", "/api/rides/" + rideId + "/rating", "{\"score\":6}"), 400,
                "VALIDATION_FAILED");

        DocumentContext ride = json(api.call(passenger, "GET", "/api/rides/" + rideId, null));
        assertThat(new BigDecimal(ride.read("$.driver.ratingAvg").toString())).isEqualByComparingTo("4.00");
        assertThat(ride.read("$.driver.ratingCount", Integer.class)).isEqualTo(1);
    }

    @Test
    void undecodableRecordsGoStraightToTheDeadLetterTopicAndTheConsumerMovesOn() throws Exception {
        String topic = names.topic(EventTopic.RIDE_COMPLETED);
        String key = UUID.randomUUID().toString();
        String garbage = "{\"not\":\"an event\"}";
        double deadLettersBefore = deadLetters(topic);

        try (TopicRecorder deadLetterTopic = new TopicRecorder(consumerFactory, List.of(KafkaNames.deadLetterTopic(topic)))) {
            kafkaTemplate.send(topic, key, garbage).get(10, TimeUnit.SECONDS);

            // Every group reading ride.completed fails on it, and each dead-letters its own copy.
            List<ConsumerRecord<Object, Object>> dead = deadLetterTopic.awaitRecords(record -> key.equals(record.key()),
                    records -> records.size() == RIDE_COMPLETED_GROUPS.size());
            assertThat(dead).extracting(record -> header(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP))
                    .containsExactlyInAnyOrderElementsOf(RIDE_COMPLETED_GROUPS.stream().map(names::group).toList());
            assertThat(dead).allSatisfy(record -> {
                assertThat(record.value()).isEqualTo(garbage);
                assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                        .isEqualTo(EventDecodingException.class.getName());
                assertThat(header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(topic);
            });
        }
        assertThat(deadLetters(topic)).isEqualTo(deadLettersBefore + RIDE_COMPLETED_GROUPS.size());
        // Every group committed past the record instead of retrying it forever.
        kafka.awaitIdle();
    }

    @Test
    void failingHandlersAreRetriedWithBackoffBeforeTheRecordIsDeadLettered() throws Exception {
        UUID rideId = completedRide(fixtures.passenger(), onlineDriver(), PaymentMethod.CASH);
        kafka.awaitIdle();
        // Break the invariant the payment handler relies on, then deliver the completion again as a new event.
        jdbc.update("DELETE FROM payments WHERE ride_id = ?", rideId);
        jdbc.update("DELETE FROM fare_breakdowns WHERE ride_id = ? AND kind = 'FINAL'", rideId);
        String replay = withNewEventId(completionEnvelope(rideId));
        String topic = names.topic(EventTopic.RIDE_COMPLETED);

        try (TopicRecorder deadLetterTopic = new TopicRecorder(consumerFactory, List.of(KafkaNames.deadLetterTopic(topic)))) {
            long sentAt = System.nanoTime();
            kafkaTemplate.send(topic, rideId.toString(), replay).get(10, TimeUnit.SECONDS);

            ConsumerRecord<Object, Object> dead = deadLetterTopic.awaitRecords(record -> replay.equals(record.value())
                    && names.group("payments").equals(header(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)))
                    .getFirst();
            assertThat(Duration.ofNanos(System.nanoTime() - sentAt)).isGreaterThanOrEqualTo(RETRY_BACKOFF_TOTAL);
            assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).isEqualTo(IllegalStateException.class.getName());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE ride_id = ?", Long.class, rideId)).isZero();
    }

    @Test
    void redeliveredEventsHaveNoSecondEffect() throws Exception {
        Actor passenger = fixtures.passenger();
        UUID rideId = completedRide(passenger, onlineDriver(), PaymentMethod.CARD);
        kafka.awaitIdle();
        String completion = completionEnvelope(rideId);
        UUID eventId = UUID.fromString(JsonPath.read(completion, "$.eventId"));
        long notificationsBefore = countNotifications(rideId);

        // The same record again, twice: what a relay retry after a lost acknowledgement produces.
        for (int delivery = 0; delivery < 2; delivery++) {
            kafkaTemplate.send(names.topic(EventTopic.RIDE_COMPLETED), rideId.toString(), completion).get(10, TimeUnit.SECONDS);
        }
        kafka.awaitIdle();

        List<Map<String, Object>> payments = jdbc.queryForList(
                "SELECT gateway, gateway_reference FROM payments WHERE ride_id = ?", rideId);
        assertThat(payments).singleElement().satisfies(payment -> {
            assertThat(payment.get("gateway")).isEqualTo("SANDBOX");
            assertThat(payment.get("gateway_reference")).isEqualTo("sandbox_" + rideId);
        });
        assertThat(countNotifications(rideId)).isEqualTo(notificationsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_analyses WHERE ride_id = ?", Long.class, rideId))
                .isOne();
        assertThat(jdbc.queryForList("SELECT consumer FROM processed_events WHERE event_id = ?", String.class, eventId))
                .containsExactlyInAnyOrderElementsOf(RIDE_COMPLETED_GROUPS);
    }

    // --- helpers ---

    private Actor onlineDriver() throws Exception {
        return RideJourneys.onlineDriver(fixtures, api);
    }

    private UUID completedRide(Actor passenger, Actor driver, PaymentMethod paymentMethod) throws Exception {
        return RideJourneys.completedRide(api, clock, passenger, driver, paymentMethod);
    }

    /** The {@code ride.completed} envelope exactly as the outbox relayed it. */
    private String completionEnvelope(UUID rideId) {
        return jdbc.queryForObject("""
                SELECT payload::text FROM outbox_events
                WHERE event_type = 'ride.completed' AND message_key = ? AND published_at IS NOT NULL
                """, String.class, rideId.toString());
    }

    private static String withNewEventId(String envelope) {
        return JsonPath.parse(envelope).set("$.eventId", UUID.randomUUID().toString()).jsonString();
    }

    private static boolean isAbout(ConsumerRecord<Object, Object> record, UUID rideId) {
        return rideId.toString().equals(envelope(record).read("$.payload.rideId", String.class));
    }

    /** Lenient: topics may also hold records other tests sent on purpose that are not event envelopes. */
    private static DocumentContext envelope(ConsumerRecord<Object, Object> record) {
        return JsonPath.using(LENIENT).parse((String) record.value());
    }

    private static Set<String> eventTypes(List<ConsumerRecord<Object, Object>> records) {
        return records.stream().map(record -> envelope(record).read("$.eventType", String.class))
                .collect(Collectors.toSet());
    }

    private List<String> notificationTypes(Actor user) {
        return jdbc.queryForList("SELECT type FROM notifications WHERE user_id = ?", String.class, user.id());
    }

    private long countNotifications(UUID rideId) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE ride_id = ?", Long.class, rideId);
    }

    private double deadLetters(String topic) {
        Counter counter = meters.find("rideflow.kafka.dead.letters").tag("topic", topic).counter();
        return counter == null ? 0 : counter.count();
    }

    private static String header(ConsumerRecord<Object, Object> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static DocumentContext json(MvcResult result) throws Exception {
        return JsonPath.parse(body(result));
    }

    private static void assertStatus(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
    }

    private static void assertError(MvcResult result, int status, String code) throws Exception {
        assertStatus(result, status);
        assertThat(JsonPath.<String>read(body(result), "$.code")).isEqualTo(code);
    }
}
