package com.rideflow.dto.realtime;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.RideStatus;
import java.time.Instant;
import java.util.UUID;

/** One ride status change for the admin live feed on {@code /topic/admin/activity}. No personal data. */
public record RideActivityMessage(UUID rideId, RideStatus previousStatus, RideStatus status, ActorType actor,
                                  long rideVersion, Instant occurredAt) {
}
