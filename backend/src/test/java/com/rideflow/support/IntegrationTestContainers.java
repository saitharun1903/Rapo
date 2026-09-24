package com.rideflow.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton PostgreSQL + PostGIS, Redis and Kafka containers shared by every integration test class in the
 * JVM, so cached Spring contexts never point at a stopped container. Tests are skipped, not failed, on machines
 * without Docker; CI runners always have Docker. Ryuk removes the containers when the JVM exits.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestContainers {

    /** Same versions as docker-compose.yml. */
    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8.6-alpine");
    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.2.1");

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGIS_IMAGE);

    @ServiceConnection
    protected static final RedisContainer REDIS = new RedisContainer(REDIS_IMAGE);

    /** Single KRaft node. Every Spring context uses its own topic prefix (application-test.yml). */
    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
            KAFKA.start();
        }
    }
}
