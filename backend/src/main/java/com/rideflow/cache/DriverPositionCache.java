package com.rideflow.cache;

import com.rideflow.config.CacheProperties;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.repository.DriverPosition;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Each driver's latest position in Redis ({@code driver:{id}:location}, a hash), written on every accepted
 * report. It is the live position the pickup geofence, the tracking snapshot and presence checks read, while
 * PostgreSQL receives positions in batches from Kafka (docs/architecture.md section 8).
 *
 * <p>A Lua script stores a report only if it is not older than the stored one, so out-of-order reports never
 * move a driver backwards. Like every Redis use here it is optional: when Redis is unavailable (or caching is
 * disabled) nothing is stored and readers fall back to PostgreSQL.
 */
@Component
public class DriverPositionCache {

    private static final Logger log = LoggerFactory.getLogger(DriverPositionCache.class);

    private static final String LAT = "lat";
    private static final String LNG = "lng";
    private static final String HEADING = "heading";
    private static final String RECORDED_AT = "recordedAt";
    private static final String UPDATED_AT = "updatedAt";

    /** ARGV[1]: recordedAt (epoch µs); ARGV[2]: TTL (ms); then field/value pairs. Returns 1 if stored. */
    private static final RedisScript<Long> STORE_IF_NEWER = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'recordedAt')
            if current and tonumber(current) > tonumber(ARGV[1]) then
              return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], unpack(ARGV, 3))
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    public enum StoreResult {
        STORED,
        /** A newer report is already stored; this one is out of order. */
        OUTDATED,
        /** Redis unavailable or caching disabled; nothing is known about ordering. */
        SKIPPED
    }

    private final StringRedisTemplate redis;
    private final CacheProperties properties;
    private final RedisAvailability availability;

    public DriverPositionCache(StringRedisTemplate redis, CacheProperties properties, RedisAvailability availability) {
        this.redis = redis;
        this.properties = properties;
        this.availability = availability;
    }

    public StoreResult store(UUID driverId, DriverPosition position) {
        if (!properties.enabled() || !availability.isAvailable()) {
            return StoreResult.SKIPPED;
        }
        List<String> args = new ArrayList<>(List.of(
                Long.toString(micros(position.recordedAt())),
                Long.toString(properties.driverLocationTtl().toMillis()),
                LAT, Double.toString(position.point().lat()),
                LNG, Double.toString(position.point().lng()),
                RECORDED_AT, Long.toString(micros(position.recordedAt())),
                UPDATED_AT, Long.toString(micros(position.updatedAt()))));
        if (position.headingDeg() != null) {
            args.add(HEADING);
            args.add(position.headingDeg().toString());
        }
        try {
            Long stored = redis.execute(STORE_IF_NEWER, List.of(RedisKeys.driverLocation(driverId)), args.toArray());
            return stored != null && stored == 1 ? StoreResult.STORED : StoreResult.OUTDATED;
        } catch (DataAccessException ex) {
            availability.recordFailure(ex);
            return StoreResult.SKIPPED;
        }
    }

    public Optional<DriverPosition> find(UUID driverId) {
        if (!properties.enabled() || !availability.isAvailable()) {
            return Optional.empty();
        }
        Map<Object, Object> fields;
        try {
            fields = redis.opsForHash().entries(RedisKeys.driverLocation(driverId));
        } catch (DataAccessException ex) {
            availability.recordFailure(ex);
            return Optional.empty();
        }
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object heading = fields.get(HEADING);
            return Optional.of(new DriverPosition(
                    new GeoPoint(Double.parseDouble((String) fields.get(LAT)), Double.parseDouble((String) fields.get(LNG))),
                    heading == null ? null : Integer.valueOf((String) heading),
                    instant((String) fields.get(RECORDED_AT)),
                    instant((String) fields.get(UPDATED_AT))));
        } catch (RuntimeException ex) {
            // Missing or malformed field (for example written by another version): fall back to PostgreSQL.
            log.warn("Ignoring unreadable cached position for driver {}: {}", driverId, ex.toString());
            return Optional.empty();
        }
    }

    private static long micros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    private static Instant instant(String micros) {
        return Instant.EPOCH.plus(Long.parseLong(micros), ChronoUnit.MICROS);
    }
}
