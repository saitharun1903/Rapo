package com.rideflow.cache;

import com.rideflow.config.CacheProperties;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Function;

/** The caches kept in Redis; each has one key pattern (see {@link RedisKeys}) and a configured TTL. */
public enum CacheName {
    ROUTE(CacheProperties::routeTtl),
    GEOCODE(CacheProperties::geocodeTtl),
    SURGE(CacheProperties::surgeTtl),
    ETA(CacheProperties::etaTtl),
    DRIVER_STATE(CacheProperties::driverStateTtl);

    private final Function<CacheProperties, Duration> ttl;

    CacheName(Function<CacheProperties, Duration> ttl) {
        this.ttl = ttl;
    }

    public Duration ttl(CacheProperties properties) {
        return ttl.apply(properties);
    }

    /** Metric tag value. */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
