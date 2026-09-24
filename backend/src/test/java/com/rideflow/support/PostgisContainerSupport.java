package com.rideflow.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton PostgreSQL + PostGIS container shared by every integration test class in the JVM, so cached
 * Spring contexts never point at a stopped container. Tests are skipped, not failed, on machines without
 * Docker; CI runners always have Docker. Ryuk removes the container when the JVM exits.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgisContainerSupport {

    /** Same versions as docker-compose.yml. */
    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres");

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = startIfDockerAvailable();

    private static PostgreSQLContainer startIfDockerAvailable() {
        PostgreSQLContainer container = new PostgreSQLContainer(POSTGIS_IMAGE);
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container.start();
        }
        return container;
    }
}
