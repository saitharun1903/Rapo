package com.rideflow.dto.rating;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RatingResponse(UUID id, UUID rideId, int score, @Nullable String comment, Instant createdAt) {
}
