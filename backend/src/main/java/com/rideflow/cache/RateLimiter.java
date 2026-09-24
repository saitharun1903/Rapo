package com.rideflow.cache;

import com.rideflow.config.RateLimitProperties;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RetryableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiting in Redis, shared by all instances. One Lua script increments the counter and
 * starts the window on the first hit, atomically, and returns the count with the window's remaining time,
 * which becomes {@code Retry-After}. The window is timed by Redis, so instance clocks do not matter.
 *
 * <p>Fails open: if Redis is unreachable the request is allowed and {@code rideflow.ratelimit.errors} is
 * incremented. An outage of an optimisation must not take logins down. Rejections are counted in
 * {@code rideflow.ratelimit.rejected{scope}}.
 *
 * <p>Trade-off: a fixed window lets a client send up to twice the limit across a window boundary. A sliding
 * window would be stricter but costs a sorted set per subject; for abuse protection the simpler bound is enough.
 */
@Component
public class RateLimiter {

    /** Raw {@code List}: the script's result type must be given as a {@code Class}, which cannot be generic. */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> FIXED_WINDOW = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final RedisAvailability availability;
    private final Map<RateLimitScope, Counter> rejected = new EnumMap<>(RateLimitScope.class);
    private final Counter errors;

    public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties, RedisAvailability availability,
                       MeterRegistry meters) {
        this.redis = redis;
        this.properties = properties;
        this.availability = availability;
        for (RateLimitScope scope : RateLimitScope.values()) {
            rejected.put(scope, Counter.builder("rideflow.ratelimit.rejected")
                    .description("Requests refused by a rate limit")
                    .tag("scope", scope.name().toLowerCase(Locale.ROOT))
                    .register(meters));
        }
        this.errors = Counter.builder("rideflow.ratelimit.errors")
                .description("Rate-limit checks skipped because Redis was unavailable (request allowed)")
                .register(meters);
    }

    /** Counts one request for {@code subject}; throws {@code 429 RATE_LIMITED} if it exceeds the limit. */
    public void acquire(RateLimitScope scope, String subject) {
        tryAcquire(scope, subject).ifPresent(retryAfter -> {
            throw new RetryableException(ErrorCode.RATE_LIMITED,
                    "Too many requests; try again in " + seconds(retryAfter) + " s", retryAfter);
        });
    }

    /** Counts one request; returns how long to wait if it exceeds the limit, or empty if it is allowed. */
    public Optional<Duration> tryAcquire(RateLimitScope scope, String subject) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        if (!availability.isAvailable()) {
            errors.increment();
            return Optional.empty();
        }
        RateLimitProperties.Rule rule = properties.rule(scope);
        List<?> result;
        try {
            result = redis.execute(FIXED_WINDOW, List.of(RedisKeys.rateLimit(scope, subject)),
                    Long.toString(rule.window().toMillis()));
        } catch (DataAccessException ex) {
            availability.recordFailure(ex);
            errors.increment();
            return Optional.empty();
        }
        long count = ((Number) result.get(0)).longValue();
        if (count <= rule.limit()) {
            return Optional.empty();
        }
        rejected.get(scope).increment();
        return Optional.of(Duration.ofMillis(((Number) result.get(1)).longValue()));
    }

    private static long seconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }
}
