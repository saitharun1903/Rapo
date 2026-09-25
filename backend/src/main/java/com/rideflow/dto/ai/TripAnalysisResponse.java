package com.rideflow.dto.ai;

import com.rideflow.ai.AIFailureCode;
import com.rideflow.ai.TripInsights;
import com.rideflow.entity.TripAnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A trip's analysis. {@code observations} are computed by the backend and always present; {@code insights} is
 * the validated AI output and is {@code null} unless the status is COMPLETED.
 *
 * @param failureCode why the AI part failed (FAILED or UNAVAILABLE), otherwise {@code null}
 * @param updatedAt   when the status last changed
 */
public record TripAnalysisResponse(
        UUID rideId,
        TripAnalysisStatus status,
        @Nullable AIFailureCode failureCode,
        List<TripObservationResponse> observations,
        @Nullable TripInsights insights,
        @Nullable String provider,
        @Nullable String model,
        String promptVersion,
        @Nullable Instant updatedAt) {
}
