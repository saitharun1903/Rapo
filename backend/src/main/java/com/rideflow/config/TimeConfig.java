package com.rideflow.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /** PostgreSQL {@code timestamptz} resolution. */
    private static final Duration DATABASE_PRECISION = Duration.ofNanos(1_000);

    /**
     * Injected wherever "now" is needed so time-dependent logic is testable. It ticks in microseconds, the
     * database's precision: with nanosecond instants, a timestamp read back from PostgreSQL (rounded to the
     * microsecond) could be later than the value written, and a duration between a stored and a fresh
     * instant could lose a whole second when truncated.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), DATABASE_PRECISION);
    }
}
