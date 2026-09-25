package com.rideflow.dto.ai;

import com.rideflow.ai.AIFailureCode;
import com.rideflow.ai.TripInsights;
import com.rideflow.entity.TripAnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        AIFailureCode failureCode,
        List<TripObservationResponse> observations,
        TripInsights insights,
        String provider,
        String model,
        String promptVersion,
        Instant updatedAt) {
}
