package com.rideflow.service.driver.event;

import com.rideflow.entity.OfflineReason;
import java.time.Instant;
import java.util.UUID;

/** The server took a driver offline (not the driver themselves). */
public record DriverWentOfflineEvent(UUID driverId, OfflineReason reason, Instant occurredAt) {
}
