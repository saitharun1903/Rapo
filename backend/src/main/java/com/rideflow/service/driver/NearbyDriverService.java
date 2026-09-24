package com.rideflow.service.driver;

import com.rideflow.config.MatchingProperties;
import com.rideflow.dto.driver.NearbyDriverResponse;
import com.rideflow.entity.Role;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.NearbyDriver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cars near a point, for the passenger map and admin views. Passengers only get positions snapped to a
 * ~110 m grid and no identity, so the endpoint cannot be used to track individual drivers.
 */
@Service
public class NearbyDriverService {

    /** 3 decimal places of latitude ~ 111 m. */
    private static final int PASSENGER_COORDINATE_SCALE = 3;
    private static final int MAX_RESULTS = 20;

    private final DriverLocationRepository driverLocations;
    private final MatchingProperties matching;
    private final Clock clock;

    public NearbyDriverService(DriverLocationRepository driverLocations, MatchingProperties matching, Clock clock) {
        this.driverLocations = driverLocations;
        this.matching = matching;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NearbyDriverResponse> find(Role viewer, GeoPoint point, int radiusMeters, VehicleCategory category) {
        List<NearbyDriver> nearby = driverLocations.findAvailableNear(point, radiusMeters, category,
                clock.instant().minus(matching.locationFreshness()), null, MAX_RESULTS);
        return nearby.stream()
                .map(driver -> viewer == Role.ADMIN
                        ? new NearbyDriverResponse(driver.position(), driver.category(), driver.driverId(),
                                driver.distanceMeters())
                        : new NearbyDriverResponse(coarsen(driver.position()), driver.category(), null, null))
                .toList();
    }

    private static GeoPoint coarsen(GeoPoint point) {
        return new GeoPoint(round(point.lat()), round(point.lng()));
    }

    private static double round(double coordinate) {
        return BigDecimal.valueOf(coordinate).setScale(PASSENGER_COORDINATE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
