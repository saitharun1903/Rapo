package com.rideflow.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Under the prod profile the backend refuses to start unless its infrastructure is named explicitly. The defaults
 * in application.yml point at localhost, and a deployment that forgot one would still report ready (readiness
 * checks only the database) with Redis, Kafka or the browser origin silently wrong.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionSettingsConfig {

    static final List<String> REQUIRED = List.of(
            "DATABASE_URL", "DATABASE_USERNAME", "REDIS_HOST", "KAFKA_BOOTSTRAP_SERVERS", "CORS_ALLOWED_ORIGINS");

    /** A bean factory post-processor, so the check runs before any bean, and any connection, is created. */
    @Bean
    static BeanFactoryPostProcessor productionSettingsCheck(Environment environment) {
        return beanFactory -> requireAll(environment);
    }

    static void requireAll(Environment environment) {
        List<String> missing = REQUIRED.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The prod profile needs " + String.join(", ", missing)
                    + " to be set (docs/deployment.md)");
        }
    }
}
