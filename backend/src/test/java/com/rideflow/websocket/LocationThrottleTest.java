package com.rideflow.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.config.RealtimeProperties;
import com.rideflow.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class LocationThrottleTest {

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final LocationThrottle throttle = new LocationThrottle(new RealtimeProperties(Duration.ofSeconds(10),
            MIN_INTERVAL, Duration.ofSeconds(10), new RealtimeProperties.Presence(Duration.ofMinutes(2), 100)),
            clock, meters);

    private double dropped() {
        return meters.get("rideflow.ws.location.dropped").counter().count();
    }

    @Test
    void allowsOneMessagePerIntervalAndCountsTheRest() {
        Map<String, Object> session = new ConcurrentHashMap<>();

        assertThat(throttle.tryAcquire(session)).isTrue();
        clock.advance(Duration.ofMillis(400));
        assertThat(throttle.tryAcquire(session)).isFalse();
        clock.advance(Duration.ofMillis(599));
        assertThat(throttle.tryAcquire(session)).isFalse();
        clock.advance(Duration.ofMillis(1));
        assertThat(throttle.tryAcquire(session)).isTrue();

        assertThat(dropped()).isEqualTo(2.0);
    }

    @Test
    void sessionsAreThrottledIndependently() {
        Map<String, Object> first = new ConcurrentHashMap<>();
        Map<String, Object> second = new ConcurrentHashMap<>();

        assertThat(throttle.tryAcquire(first)).isTrue();
        assertThat(throttle.tryAcquire(second)).isTrue();
        assertThat(dropped()).isZero();
    }
}
