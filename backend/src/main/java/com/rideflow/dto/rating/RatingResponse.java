package com.rideflow.dto.rating;

import java.time.Instant;
import java.util.UUID;

public record RatingResponse(UUID id, UUID rideId, int score, String comment, Instant createdAt) {
}
