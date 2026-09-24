package com.rideflow.cache;

import com.rideflow.config.CacheProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-through cache of JSON values in Redis with a per-cache TTL. Redis is an optimisation here, never a
 * dependency: if it is unreachable, or a stored value cannot be read (for example after a schema change), the
 * value is computed as if it were not cached. Metrics: {@code rideflow.cache.requests{cache, result}} with
 * result {@code hit}, {@code miss}, {@code error} or {@code bypass} (caching disabled).
 */
@Component
public class JsonCache {

    private static final Logger log = LoggerFactory.getLogger(JsonCache.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final CacheProperties properties;
    private final RedisAvailability availability;
    private final Map<CacheName, Map<Result, Counter>> counters = new EnumMap<>(CacheName.class);

    private enum Result {
        HIT, MISS, ERROR, BYPASS
    }

    public JsonCache(StringRedisTemplate redis, JsonMapper jsonMapper, CacheProperties properties,
                     RedisAvailability availability, MeterRegistry meters) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.availability = availability;
        for (CacheName cache : CacheName.values()) {
            Map<Result, Counter> byResult = new EnumMap<>(Result.class);
            for (Result result : Result.values()) {
                byResult.put(result, Counter.builder("rideflow.cache.requests")
                        .description("Cache lookups by outcome")
                        .tag("cache", cache.tag())
                        .tag("result", result.name().toLowerCase(Locale.ROOT))
                        .register(meters));
            }
            counters.put(cache, byResult);
        }
    }

    /**
     * The cached value, or {@code loader}'s result, which is stored only if {@code storeIf} accepts it (so, for
     * example, a degraded fallback answer is not kept for the whole TTL).
     */
    public <T> T getOrCompute(CacheName cache, String key, Class<T> type, Supplier<T> loader,
                              Predicate<? super T> storeIf) {
        if (!properties.enabled()) {
            count(cache, Result.BYPASS);
            return loader.get();
        }
        Optional<T> cached = read(cache, key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = loader.get();
        if (value != null && storeIf.test(value)) {
            put(cache, key, value);
        }
        return value;
    }

    /** The cached value if present, without computing anything. */
    public <T> Optional<T> peek(CacheName cache, String key, Class<T> type) {
        if (!properties.enabled()) {
            count(cache, Result.BYPASS);
            return Optional.empty();
        }
        return read(cache, key, type);
    }

    public void put(CacheName cache, String key, Object value) {
        if (!properties.enabled() || !availability.isAvailable()) {
            return;
        }
        try {
            redis.opsForValue().set(key, jsonMapper.writeValueAsString(value), cache.ttl(properties));
        } catch (DataAccessException ex) {
            availability.recordFailure(ex);
        }
    }

    /**
     * Sets {@code key} only if absent, with {@code ttl}. Used as a short-lived claim so that one caller does a
     * piece of work. When Redis is unavailable nobody can claim, so the work is skipped rather than repeated
     * by every caller.
     */
    public boolean claim(String key, Duration ttl) {
        if (!properties.enabled() || !availability.isAvailable()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (DataAccessException ex) {
            availability.recordFailure(ex);
            return false;
        }
    }

    public void evict(String... keys) {
        if (!availability.isAvailable()) {
            return;
        }
        try {
            redis.delete(List.of(keys));
        } catch (DataAccessException ex) {
            availability.recordFailure(ex);
        }
    }

    private <T> Optional<T> read(CacheName cache, String key, Class<T> type) {
        if (!availability.isAvailable()) {
            count(cache, Result.ERROR);
            return Optional.empty();
        }
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            availability.recordFailure(ex);
            count(cache, Result.ERROR);
            return Optional.empty();
        }
        if (json == null) {
            count(cache, Result.MISS);
            return Optional.empty();
        }
        try {
            T value = jsonMapper.readValue(json, type);
            count(cache, Result.HIT);
            return Optional.of(value);
        } catch (JacksonException ex) {
            // Unreadable entry (for example written by an older version): recompute and overwrite it.
            log.warn("Discarding unreadable {} cache entry: {}", cache.tag(), ex.getOriginalMessage());
            count(cache, Result.ERROR);
            return Optional.empty();
        }
    }

    private void count(CacheName cache, Result result) {
        counters.get(cache).get(result).increment();
    }
}
