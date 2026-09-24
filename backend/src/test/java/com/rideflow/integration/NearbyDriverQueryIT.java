package com.rideflow.integration;

import static com.rideflow.support.GeoTestPoints.HITECH_CITY;
import static com.rideflow.support.GeoTestPoints.offset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.rideflow.entity.VehicleCategory;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.NearbyDriver;
import com.rideflow.support.MutableClock;
import com.rideflow.support.IntegrationTestContainers;
import com.rideflow.support.RideFixtures;
import com.rideflow.support.RideFixtures.Actor;
import com.rideflow.support.RideTestConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** The PostGIS proximity query against real geometry: radius, ordering, freshness and eligibility filters. */
@SpringBootTest
@ActiveProfiles("test")
@Import(RideTestConfig.class)
class NearbyDriverQueryIT extends IntegrationTestContainers {

    private static final Duration FRESHNESS = Duration.ofSeconds(30);

    @Autowired
    private RideFixtures fixtures;
    @Autowired
    private DriverLocationRepository locations;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;

    private Actor place(VehicleCategory category, double northMeters, double eastMeters) {
        return placeAt(category, northMeters, eastMeters, clock.instant());
    }

    private Actor placeAt(VehicleCategory category, double northMeters, double eastMeters, Instant reportedAt) {
        Actor driver = fixtures.verifiedDriver(category);
        jdbc.update("UPDATE drivers SET availability = 'AVAILABLE' WHERE id = ?", driver.id());
        locations.upsert(driver.id(), offset(HITECH_CITY, northMeters, eastMeters), null, null, null,
                reportedAt, reportedAt);
        return driver;
    }

    private List<NearbyDriver> search(double radius, VehicleCategory category) {
        return locations.findAvailableNear(HITECH_CITY, radius, category, clock.instant().minus(FRESHNESS), null, 10);
    }

    @BeforeEach
    void setUp() {
        fixtures.reset();
    }

    @Test
    void returnsDriversWithinRadiusNearestFirstWithTrueDistance() {
        Actor far = place(VehicleCategory.ECONOMY, 2_000, 0);
        Actor near = place(VehicleCategory.ECONOMY, 0, 300);
        Actor middle = place(VehicleCategory.ECONOMY, -900, 0);
        place(VehicleCategory.ECONOMY, 4_000, 0); // outside 3 km

        List<NearbyDriver> result = search(3_000, null);

        assertThat(result).extracting(NearbyDriver::driverId).containsExactly(near.id(), middle.id(), far.id());
        assertThat(result.get(0).distanceMeters()).isCloseTo(300, within(5.0));
        assertThat(result.get(1).distanceMeters()).isCloseTo(900, within(10.0));
        assertThat(result.get(2).distanceMeters()).isCloseTo(2_000, within(20.0));
    }

    @Test
    void filtersByVehicleCategory() {
        place(VehicleCategory.ECONOMY, 100, 0);
        Actor xl = place(VehicleCategory.XL, 200, 0);

        assertThat(search(3_000, VehicleCategory.XL)).extracting(NearbyDriver::driverId).containsExactly(xl.id());
    }

    @Test
    void ignoresStaleOfflineAndUnverifiedDrivers() {
        Actor fresh = place(VehicleCategory.ECONOMY, 100, 0);
        // Last report two minutes ago: beyond the 30 s freshness window.
        placeAt(VehicleCategory.ECONOMY, 150, 0, clock.instant().minus(Duration.ofMinutes(2)));
        Actor offline = place(VehicleCategory.ECONOMY, 200, 0);
        jdbc.update("UPDATE drivers SET availability = 'OFFLINE' WHERE id = ?", offline.id());
        Actor suspended = place(VehicleCategory.ECONOMY, 250, 0);
        jdbc.update("UPDATE drivers SET availability = 'OFFLINE', verification_status = 'SUSPENDED' WHERE id = ?",
                suspended.id());

        assertThat(search(3_000, null)).extracting(NearbyDriver::driverId).containsExactly(fresh.id());
    }

    @Test
    void olderReportNeverOverwritesNewerPosition() {
        Actor driver = place(VehicleCategory.ECONOMY, 100, 0);
        Instant earlier = clock.instant().minusSeconds(10);
        locations.upsert(driver.id(), offset(HITECH_CITY, 5_000, 0), null, null, null, earlier, clock.instant());

        assertThat(locations.find(driver.id()).orElseThrow().point().lat())
                .isCloseTo(offset(HITECH_CITY, 100, 0).lat(), within(1e-6));
    }

    @Test
    void spatialIndexServesTheProximityQuery() {
        place(VehicleCategory.ECONOMY, 100, 0);
        // With only a handful of rows the planner rightly prefers a sequential scan, so disable it for this
        // transaction to prove the GiST index is usable for both ST_DWithin and the KNN ordering.
        List<String> plan = tx.execute(status -> {
            jdbc.execute("SET LOCAL enable_seqscan = off");
            return jdbc.queryForList("""
                    EXPLAIN SELECT driver_id FROM driver_locations
                    WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(78.3772, 17.4435), 4326)::geography, 3000)
                    ORDER BY location <-> ST_SetSRID(ST_MakePoint(78.3772, 17.4435), 4326)::geography LIMIT 10
                    """, String.class);
        });

        assertThat(String.join(System.lineSeparator(), plan)).contains("ix_driver_locations_location");
    }

    @Test
    void excludesDriversAlreadyHoldingAPendingOffer() {
        Actor busy = place(VehicleCategory.ECONOMY, 100, 0);
        Actor free = place(VehicleCategory.ECONOMY, 200, 0);
        UUID otherRide = createPlaceholderRide();
        jdbc.update("""
                INSERT INTO ride_offers (ride_id, driver_id, round, distance_m, status, offered_at, expires_at)
                VALUES (?, ?, 1, 100, 'PENDING', now(), now() + interval '20 seconds')
                """, otherRide, busy.id());

        List<NearbyDriver> result = locations.findAvailableNear(HITECH_CITY, 3_000, null,
                clock.instant().minus(FRESHNESS), UUID.randomUUID(), 10);

        assertThat(result).extracting(NearbyDriver::driverId).containsExactly(free.id());
    }

    private UUID createPlaceholderRide() {
        Actor passenger = fixtures.passenger();
        UUID rideId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rides (id, passenger_id, status, vehicle_category, pickup_lat, pickup_lng, pickup_address,
                    dropoff_lat, dropoff_lng, dropoff_address, payment_method, estimated_distance_m, estimated_duration_s,
                    estimate_source, surge_multiplier, currency, requested_at)
                VALUES (?, ?, 'MATCHING', 'ECONOMY', 17.44, 78.38, 'A', 17.42, 78.47, 'B', 'CASH', 1000, 100,
                    'APPROXIMATE', 1.00, 'INR', now())
                """, rideId, passenger.id());
        return rideId;
    }
}
