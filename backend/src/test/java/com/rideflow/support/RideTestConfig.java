package com.rideflow.support;

import com.rideflow.repository.DriverRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.repository.VehicleRepository;
import com.rideflow.security.AccessTokenService;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Replaces the system clock with a controllable one and exposes ride fixtures. */
@TestConfiguration(proxyBeanMethods = false)
public class RideTestConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.now());
    }

    @Bean
    RideFixtures rideFixtures(UserRepository users, DriverRepository drivers, VehicleRepository vehicles,
                              AccessTokenService accessTokens, JdbcTemplate jdbc, TransactionTemplate tx,
                              MutableClock testClock) {
        return new RideFixtures(users, drivers, vehicles, accessTokens, jdbc, tx, testClock);
    }
}
