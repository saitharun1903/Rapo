package com.rideflow.dto.ride;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.RideStatus;
import java.time.Instant;

public record RideTimelineEntryResponse(
        RideStatus from, RideStatus to, ActorType actor, String reason, long rideVersion, Instant occurredAt) {
}
