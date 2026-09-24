package com.rideflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.DefaultTaskSchedulerConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the sweepers. The WebSocket broker defines its own {@code messageBrokerTaskScheduler}, which makes
 * Spring Boot skip its default scheduler; {@code @Scheduled} jobs would then run on the broker's heartbeat
 * pool. Importing Boot's default scheduler keeps the two apart ({@code @Scheduled} picks the bean named
 * {@code taskScheduler}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Import(DefaultTaskSchedulerConfiguration.class)
@ConditionalOnProperty(name = "rideflow.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
