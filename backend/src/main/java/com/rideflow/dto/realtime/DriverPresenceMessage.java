package com.rideflow.dto.realtime;

import com.rideflow.entity.DriverAvailability;
import com.rideflow.entity.OfflineReason;
import java.time.Instant;

/** Tells a driver on {@code /user/queue/presence} that the server changed their availability. */
public record DriverPresenceMessage(DriverAvailability availability, OfflineReason reason, Instant occurredAt) {
}
