package com.rideflow.websocket;

import com.rideflow.config.RealtimeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * At most one location message per interval per WebSocket session; excess messages are dropped (a newer
 * report follows within the second) and counted. State lives in the session's attributes, so it disappears
 * with the session and needs no cleanup. Each driver has one session, so this is effectively per driver.
 */
@Component
public class LocationThrottle {

    private final Duration minInterval;
    private final Clock clock;
    private final Counter dropped;

    public LocationThrottle(RealtimeProperties properties, Clock clock, MeterRegistry meters) {
        this.minInterval = properties.locationMinInterval();
        this.clock = clock;
        this.dropped = Counter.builder("rideflow.ws.location.dropped")
                .description("Location messages dropped because they arrived faster than the minimum interval")
                .register(meters);
    }

    /** {@code true} if the message may be processed; {@code false} if it must be dropped. */
    public boolean tryAcquire(Map<String, Object> sessionAttributes) {
        @SuppressWarnings("unchecked")
        AtomicReference<Instant> last = (AtomicReference<Instant>) sessionAttributes.computeIfAbsent(
                SessionAttributes.LAST_LOCATION_AT, key -> new AtomicReference<Instant>());
        Instant now = clock.instant();
        while (true) {
            Instant previous = last.get();
            if (previous != null && now.isBefore(previous.plus(minInterval))) {
                dropped.increment();
                return false;
            }
            if (last.compareAndSet(previous, now)) {
                return true;
            }
        }
    }
}
