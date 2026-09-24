package com.rideflow.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.config.CacheProperties;
import com.rideflow.config.RateLimitProperties;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RetryableException;
import com.rideflow.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RateLimiterTest {

    private static final int LOGIN_LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RedisAvailability availability = new RedisAvailability(new CacheProperties(true,
            Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofSeconds(60), Duration.ofSeconds(30),
            Duration.ofSeconds(5)), clock, meters);

    private RateLimiter limiter(boolean enabled) {
        Map<RateLimitScope, RateLimitProperties.Rule> rules = new EnumMap<>(RateLimitScope.class);
        for (RateLimitScope scope : RateLimitScope.values()) {
            rules.put(scope, new RateLimitProperties.Rule(LOGIN_LIMIT, WINDOW));
        }
        return new RateLimiter(redis, new RateLimitProperties(enabled, rules), availability, meters);
    }

    @SuppressWarnings("unchecked")
    private void redisCountIs(long count, long remainingMillis) {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of(count, remainingMillis));
    }

    @Test
    void allowsRequestsUpToTheLimit() {
        redisCountIs(LOGIN_LIMIT, 40_000);

        assertThat(limiter(true).tryAcquire(RateLimitScope.LOGIN, "ip|email")).isEmpty();
    }

    @Test
    void rejectsBeyondTheLimitWithTheWindowsRemainingTimeRoundedUp() {
        redisCountIs(LOGIN_LIMIT + 1, 41_200);

        assertThatThrownBy(() -> limiter(true).acquire(RateLimitScope.LOGIN, "ip|email"))
                .isInstanceOfSatisfying(RetryableException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(ex.retryAfterSeconds()).isEqualTo(42);
                });
        assertThat(meters.get("rideflow.ratelimit.rejected").tag("scope", "login").counter().count()).isEqualTo(1);
    }

    @Test
    void disabledLimiterNeverTouchesRedis() {
        limiter(false).acquire(RateLimitScope.LOGIN, "ip|email");

        verifyNoInteractions(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsOpenWhenRedisIsDownAndStopsCallingItForAWhile() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        RateLimiter limiter = limiter(true);

        assertThat(limiter.tryAcquire(RateLimitScope.LOGIN, "ip|email")).isEmpty();
        assertThat(limiter.tryAcquire(RateLimitScope.LOGIN, "ip|email")).isEmpty();

        // The second check skipped Redis entirely; both were allowed and counted.
        verify(redis, times(1)).execute(any(RedisScript.class), anyList(), anyString());
        assertThat(meters.get("rideflow.ratelimit.errors").counter().count()).isEqualTo(2);
        assertThat(availability.isAvailable()).isFalse();

        clock.advance(Duration.ofSeconds(5));
        assertThat(availability.isAvailable()).isTrue();
    }
}
