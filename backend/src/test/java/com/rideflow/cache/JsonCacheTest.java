package com.rideflow.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.config.CacheProperties;
import com.rideflow.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class JsonCacheTest {

    private static final Duration SURGE_TTL = Duration.ofSeconds(60);
    private static final String KEY = "surge:tepg5x";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    private JsonCache cache(boolean enabled) {
        CacheProperties properties = new CacheProperties(enabled, Duration.ofMinutes(15), Duration.ofHours(24),
                SURGE_TTL, Duration.ofSeconds(30), Duration.ofSeconds(5));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
        return new JsonCache(redis, JsonMapper.builder().build(), properties,
                new RedisAvailability(properties, clock, meters), meters);
    }

    private BigDecimal load() {
        loads.incrementAndGet();
        return new BigDecimal("1.40");
    }

    private double count(String result) {
        return meters.get("rideflow.cache.requests").tag("cache", "surge").tag("result", result).counter().count();
    }

    @Test
    void missComputesAndStoresWithTheCachesTtl() {
        BigDecimal value = cache(true).getOrCompute(CacheName.SURGE, KEY, BigDecimal.class, this::load, v -> true);

        assertThat(value).isEqualByComparingTo("1.40");
        verify(values).set(KEY, "1.40", SURGE_TTL);
        assertThat(count("miss")).isEqualTo(1);
    }

    @Test
    void hitReturnsTheStoredValueWithoutComputing() {
        when(values.get(KEY)).thenReturn("1.70");

        BigDecimal value = cache(true).getOrCompute(CacheName.SURGE, KEY, BigDecimal.class, this::load, v -> true);

        assertThat(value).isEqualByComparingTo("1.70");
        assertThat(loads).hasValue(0);
        assertThat(count("hit")).isEqualTo(1);
    }

    @Test
    void valuesRejectedByTheStorePolicyAreServedButNotStored() {
        cache(true).getOrCompute(CacheName.SURGE, KEY, BigDecimal.class, this::load, v -> false);

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisFailureFallsBackToComputing() {
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("connection refused"));

        BigDecimal value = cache(true).getOrCompute(CacheName.SURGE, KEY, BigDecimal.class, this::load, v -> true);

        assertThat(value).isEqualByComparingTo("1.40");
        assertThat(count("error")).isEqualTo(1);
        // Redis is now skipped, so the result is not written back either.
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void unreadableEntryIsRecomputedAndOverwritten() {
        when(values.get(KEY)).thenReturn("{not a number");

        BigDecimal value = cache(true).getOrCompute(CacheName.SURGE, KEY, BigDecimal.class, this::load, v -> true);

        assertThat(value).isEqualByComparingTo("1.40");
        verify(values).set(eq(KEY), eq("1.40"), eq(SURGE_TTL));
    }

    @Test
    void disabledCacheBypassesRedis() {
        BigDecimal value = cache(false).getOrCompute(CacheName.SURGE, KEY, BigDecimal.class, this::load, v -> true);

        assertThat(value).isEqualByComparingTo("1.40");
        verifyNoInteractions(values);
        assertThat(count("bypass")).isEqualTo(1);
    }
}
