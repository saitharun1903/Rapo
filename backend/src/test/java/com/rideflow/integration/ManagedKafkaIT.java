package com.rideflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.config.KafkaConfig;
import com.rideflow.kafka.event.EventTopic;
import com.rideflow.kafka.event.KafkaNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * The Kafka client settings of a managed broker (docs/deployment.md), set through the same environment variables
 * as the deployment, against Redpanda with SASL/SCRAM and authorization on: the application's admin creates every
 * topic with the configured partition count, a record makes the round trip, and a wrong password is refused.
 * The container speaks SASL without TLS (SASL_PLAINTEXT); TLS is the only difference from the managed service.
 * Only the Kafka beans are started, so the test needs no database.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ManagedKafkaIT.KafkaOnly.class, properties = {
        "KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT",
        "KAFKA_SASL_MECHANISM=SCRAM-SHA-256",
        "KAFKA_USERNAME=" + ManagedKafkaIT.USERNAME,
        "KAFKA_PASSWORD=" + ManagedKafkaIT.PASSWORD,
        "KAFKA_PARTITIONS=1",
        "KAFKA_TOPIC_PREFIX=managed-it.",
})
class ManagedKafkaIT {

    static final String USERNAME = "rideflow";
    static final String PASSWORD = "managed-it-password";
    private static final String SCRAM = "SCRAM-SHA-256";
    private static final Duration AWAIT = Duration.ofSeconds(20);
    private static final int OK = 200;

    private static final RedpandaContainer REDPANDA = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v26.2.3")
            .enableAuthorization()
            .enableSasl()
            .withSuperuser(USERNAME);

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            REDPANDA.start();
            createUser();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(KafkaAutoConfiguration.class)
    @Import({KafkaConfig.class, KafkaNames.class})
    static class KafkaOnly {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BOOTSTRAP_SERVERS", REDPANDA::getBootstrapServers);
    }

    @Autowired
    private KafkaAdmin admin;
    @Autowired
    private KafkaNames names;
    @Autowired
    private KafkaTemplate<String, String> template;
    @Autowired
    private ConsumerFactory<String, String> consumers;

    @Test
    void theApplicationCreatesEveryTopicAndDeadLetterTopicWithTheConfiguredPartitions() {
        List<String> expected = new ArrayList<>();
        for (EventTopic topic : EventTopic.values()) {
            expected.add(names.topic(topic));
            expected.add(KafkaNames.deadLetterTopic(names.topic(topic)));
        }

        Map<String, TopicDescription> described = admin.describeTopics(expected.toArray(String[]::new));

        assertThat(described).hasSize(expected.size());
        assertThat(described.values()).allSatisfy(topic -> assertThat(topic.partitions()).hasSize(1));
    }

    @Test
    void aRecordMakesTheRoundTrip() throws Exception {
        String topic = names.topic(EventTopic.PAYMENT_CREATED);
        String value = UUID.randomUUID().toString();

        long offset = template.send(topic, value, value).get(AWAIT.toSeconds(), TimeUnit.SECONDS)
                .getRecordMetadata().offset();

        try (Consumer<String, String> consumer = consumers.createConsumer("managed-it-" + value, null)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seek(partition, offset);
            List<String> received = new ArrayList<>();
            long deadline = System.nanoTime() + AWAIT.toNanos();
            while (received.isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofSeconds(1)).forEach((ConsumerRecord<String, String> r) -> received.add(r.value()));
            }
            assertThat(received).containsExactly(value);
        }
    }

    @Test
    void aWrongPasswordIsRefused() {
        Map<String, Object> config = new HashMap<>(admin.getConfigurationProperties());
        String jaas = (String) config.get(SaslConfigs.SASL_JAAS_CONFIG);
        assertThat(jaas).contains("password=\"" + PASSWORD + "\"");
        config.put(SaslConfigs.SASL_JAAS_CONFIG, jaas.replace(PASSWORD, "not-" + PASSWORD));

        try (Admin intruder = Admin.create(config)) {
            assertThatThrownBy(() -> intruder.describeCluster().nodes().get(AWAIT.toSeconds(), TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(SaslAuthenticationException.class);
        }
    }

    /** Redpanda's admin API creates the SCRAM user that {@code withSuperuser} grants every permission. */
    private static void createUser() {
        String body = "{\"username\":\"%s\",\"password\":\"%s\",\"algorithm\":\"%s\"}".formatted(USERNAME, PASSWORD, SCRAM);
        HttpRequest request = HttpRequest.newBuilder(URI.create(REDPANDA.getAdminAddress() + "/v1/security/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != OK) {
                throw new IllegalStateException("Creating the SCRAM user failed: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Creating the SCRAM user failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the SCRAM user", e);
        }
    }
}
