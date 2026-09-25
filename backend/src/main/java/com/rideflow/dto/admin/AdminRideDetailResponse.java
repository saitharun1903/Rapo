package com.rideflow.dto.admin;

import com.rideflow.dto.ride.RideResponse;
import com.rideflow.dto.ride.RideTimelineEntryResponse;
import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.TripAnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Everything about one ride for support: the ride as its participants see it, who booked it, its status
 * history, every offer made to drivers, and whether its AI analysis succeeded.
 *
 * @param analysis {@code null} until the ride is completed and its analysis is recorded
 */
public record AdminRideDetailResponse(
        RideResponse ride,
        @Nullable Passenger passenger,
        List<RideTimelineEntryResponse> timeline,
        List<Offer> offers,
        @Nullable Analysis analysis) {

    @Schema(name = "AdminRidePassenger")
    public record Passenger(UUID id, String fullName, String email) {
    }

    @Schema(name = "AdminRideOffer")
    public record Offer(UUID driverId, int round, int distanceMeters, OfferStatus status, Instant offeredAt,
                        Instant expiresAt, @Nullable Instant respondedAt) {
    }

    @Schema(name = "AdminRideAnalysis")
    public record Analysis(TripAnalysisStatus status, @Nullable String failureCode, Instant updatedAt) {
    }
}
