package com.rideflow.service.fare;

import com.rideflow.cache.CacheName;
import com.rideflow.cache.JsonCache;
import com.rideflow.cache.RedisKeys;
import com.rideflow.config.MatchingProperties;
import com.rideflow.config.SurgeProperties;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.Geohash;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.RideRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Live surge from real demand (open requests) and supply (fresh available drivers) around a pickup.
 *
 * <p>Cached per geohash cell (precision 6, about 1.2 km × 0.6 km) for {@code rideflow.cache.surge-ttl}: each
 * computation is two PostGIS radius counts, and every estimate needs one. The value is computed at the
 * cell's centre so that it depends only on the cache key; with a 2 km counting radius that moves the
 * sample point by at most ~700 m. The TTL (60 s) bounds how stale surge can be relative to real demand.
 */
@Service
public class SurgeService {

    private static final BigDecimal NO_SURGE = BigDecimal.ONE.setScale(2);
    private static final int CELL_PRECISION = 6;

    private final SurgeProperties properties;
    private final SurgeCalculator calculator;
    private final RideRepository rides;
    private final DriverLocationRepository driverLocations;
    private final MatchingProperties matching;
    private final JsonCache cache;
    private final Clock clock;

    public SurgeService(SurgeProperties properties, SurgeCalculator calculator, RideRepository rides,
                        DriverLocationRepository driverLocations, MatchingProperties matching, JsonCache cache,
                        Clock clock) {
        this.properties = properties;
        this.calculator = calculator;
        this.rides = rides;
        this.driverLocations = driverLocations;
        this.matching = matching;
        this.cache = cache;
        this.clock = clock;
    }

    /** Not transactional: a cache hit must not take a database connection, and the two counts are independent. */
    public BigDecimal multiplierAt(GeoPoint pickup) {
        if (!properties.enabled()) {
            return NO_SURGE;
        }
        String cell = Geohash.encode(pickup, CELL_PRECISION);
        return cache.getOrCompute(CacheName.SURGE, RedisKeys.surge(cell), BigDecimal.class,
                () -> compute(Geohash.center(cell)), multiplier -> true);
    }

    private BigDecimal compute(GeoPoint point) {
        Instant now = clock.instant();
        long demand = rides.countOpenRequestsNear(
                point.lat(), point.lng(), properties.radiusMeters(), now.minus(properties.demandWindow()));
        long supply = driverLocations.countAvailableNear(
                point, properties.radiusMeters(), now.minus(matching.locationFreshness()));
        return calculator.multiplier(demand, supply);
    }
}
