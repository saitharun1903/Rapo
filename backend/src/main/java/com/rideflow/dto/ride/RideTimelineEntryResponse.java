package com.rideflow.dto.ride;

import com.rideflow.entity.ActorType;
import com.rideflow.entity.RideStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record RideTimelineEntryResponse(
        @Nullable RideStatus from, RideStatus to, ActorType actor, @Nullable String reason, long rideVersion,
        Instant occurredAt) {
}
