package com.rideflow.service.driver.event;

import com.rideflow.entity.OfflineReason;
import com.rideflow.service.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** The server took a driver offline (not the driver themselves). */
public record DriverWentOfflineEvent(UUID driverId, OfflineReason reason, Instant occurredAt) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return driverId;
    }
}
