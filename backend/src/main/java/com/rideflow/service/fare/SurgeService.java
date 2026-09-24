package com.rideflow.service.fare;

import com.rideflow.config.MatchingProperties;
import com.rideflow.config.SurgeProperties;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.RideRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Live surge around a pickup from real demand (open requests) and supply (fresh available drivers). */
@Service
public class SurgeService {

    private static final BigDecimal NO_SURGE = BigDecimal.ONE.setScale(2);

    private final SurgeProperties properties;
    private final SurgeCalculator calculator;
    private final RideRepository rides;
    private final DriverLocationRepository driverLocations;
    private final MatchingProperties matching;
    private final Clock clock;

    public SurgeService(SurgeProperties properties, SurgeCalculator calculator, RideRepository rides,
                        DriverLocationRepository driverLocations, MatchingProperties matching, Clock clock) {
        this.properties = properties;
        this.calculator = calculator;
        this.rides = rides;
        this.driverLocations = driverLocations;
        this.matching = matching;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BigDecimal multiplierAt(GeoPoint pickup) {
        if (!properties.enabled()) {
            return NO_SURGE;
        }
        Instant now = clock.instant();
        long demand = rides.countOpenRequestsNear(
                pickup.lat(), pickup.lng(), properties.radiusMeters(), now.minus(properties.demandWindow()));
        long supply = driverLocations.countAvailableNear(
                pickup, properties.radiusMeters(), now.minus(matching.locationFreshness()));
        return calculator.multiplier(demand, supply);
    }
}
