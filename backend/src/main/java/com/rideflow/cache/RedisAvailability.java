package com.rideflow.cache;

import com.rideflow.config.CacheProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A minimal circuit breaker for Redis. Everything the application keeps in Redis is optional (caches and
 * rate limits fail open), so when Redis is down each request should pay for at most one timeout, not one per
 * Redis call. After a failure, Redis is skipped for {@code rideflow.cache.redis-retry-after}; the next
 * call after that probes it again. Exposes {@code rideflow.redis.available} (1 or 0).
 */
@Component
public final class RedisAvailability {

    private static final Logger log = LoggerFactory.getLogger(RedisAvailability.class);

    private final Duration retryAfter;
    private final Clock clock;
    private final AtomicReference<Instant> unavailableUntil = new AtomicReference<>(Instant.MIN);

    public RedisAvailability(CacheProperties properties, Clock clock, MeterRegistry meters) {
        this.retryAfter = properties.redisRetryAfter();
        this.clock = clock;
        Gauge.builder("rideflow.redis.available", this, availability -> availability.isAvailable() ? 1 : 0)
                .description("1 while Redis calls are attempted, 0 while they are skipped after a failure")
                .register(meters);
    }

    public boolean isAvailable() {
        return !clock.instant().isBefore(unavailableUntil.get());
    }

    public void recordFailure(RuntimeException ex) {
        Instant until = clock.instant().plus(retryAfter);
        if (unavailableUntil.getAndSet(until).isBefore(clock.instant())) {
            log.warn("Redis unavailable, skipping it for {}: {}", retryAfter, ex.getMessage());
        }
    }
}
