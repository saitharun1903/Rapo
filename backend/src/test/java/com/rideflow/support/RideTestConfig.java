package com.rideflow.support;

import com.rideflow.kafka.event.KafkaNames;
import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.OutboxRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.security.AccessTokenService;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.transaction.support.TransactionTemplate;

/** Replaces the system clock with a controllable one and exposes ride fixtures and Kafka test helpers. */
@TestConfiguration(proxyBeanMethods = false)
public class RideTestConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.now());
    }

    @Bean
    KafkaTestSupport kafkaTestSupport(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin, KafkaNames names,
                                      OutboxRepository outbox) {
        return new KafkaTestSupport(registry, kafkaAdmin, names, outbox);
    }

    @Bean
    RideFixtures rideFixtures(UserRepository users, DriverRepository drivers, VehicleRepository vehicles,
                              AccessTokenService accessTokens, JdbcTemplate jdbc, TransactionTemplate tx,
                              MutableClock testClock, StringRedisTemplate redis, KafkaTestSupport kafka) {
        return new RideFixtures(users, drivers, vehicles, accessTokens, jdbc, tx, testClock, redis, kafka);
    }
}
