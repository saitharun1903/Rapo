package com.rideflow.service.driver;

import com.rideflow.config.RideProperties;
import com.rideflow.entity.RideStatus;
import com.rideflow.repository.DriverLocationRepository;
import com.rideflow.repository.DriverLocationRepository.LocationWrite;
import com.rideflow.repository.RideTrackPointRepository;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a batch of position reports from {@code driver.location.updated} to PostgreSQL: one upsert per
 * driver (only their latest report in the batch), and sampled track points for trips in progress. This is
 * what keeps per-report database writes off the ingestion path. Replaying a batch is harmless: positions
 * never move backwards and a repeated track point is within the sampling distance of itself.
 */
@Service
public class LocationPersistenceService {

    private final DriverLocationRepository driverLocations;
    private final RideTrackPointRepository trackPoints;
    private final RideProperties.Location rules;

    public LocationPersistenceService(DriverLocationRepository driverLocations, RideTrackPointRepository trackPoints,
                                      RideProperties properties) {
        this.driverLocations = driverLocations;
        this.trackPoints = trackPoints;
        this.rules = properties.location();
    }

    @Transactional
    public void persist(List<DriverLocationUpdatedEvent> reports) {
        Map<UUID, DriverLocationUpdatedEvent> latest = new LinkedHashMap<>();
        for (DriverLocationUpdatedEvent report : reports) {
            latest.merge(report.driverId(), report,
                    (kept, candidate) -> candidate.recordedAt().isAfter(kept.recordedAt()) ? candidate : kept);
        }
        driverLocations.upsertAll(latest.values().stream()
                .map(report -> new LocationWrite(report.driverId(), report.location(), report.headingDeg(),
                        report.speedMps(), report.accuracyMeters(), report.recordedAt(), report.receivedAt()))
                .toList());
        reports.stream()
                .filter(report -> report.rideId() != null && report.rideStatus() == RideStatus.IN_PROGRESS)
                .sorted(Comparator.comparing(DriverLocationUpdatedEvent::recordedAt))
                .forEach(report -> trackPoints.appendIfSampled(report.rideId(), report.location(), report.recordedAt(),
                        rules.trackMinInterval(), rules.trackMinDistanceMeters()));
    }
}
