package com.rideflow.service.admin;

import com.rideflow.dto.admin.AdminRideDetailResponse;
import com.rideflow.dto.admin.AdminRideSummaryResponse;
import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.ride.RideSummaryResponse;
import com.rideflow.dto.ride.RideTimelineEntryResponse;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.repository.RideOfferRepository;
import com.rideflow.repository.RideRepository;
import com.rideflow.repository.RideSpecifications;
import com.rideflow.repository.RideStatusEventRepository;
import com.rideflow.repository.TripAnalysisRepository;
import com.rideflow.repository.UserRepository;
import com.rideflow.service.ride.RideViewAssembler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to every ride, for admins supporting passengers and drivers. */
@Service
@Transactional(readOnly = true)
public class AdminRideService {

    private final RideRepository rides;
    private final RideStatusEventRepository statusEvents;
    private final RideOfferRepository offers;
    private final UserRepository users;
    private final TripAnalysisRepository analyses;
    private final RideViewAssembler views;

    public AdminRideService(RideRepository rides, RideStatusEventRepository statusEvents, RideOfferRepository offers,
                            UserRepository users, TripAnalysisRepository analyses, RideViewAssembler views) {
        this.rides = rides;
        this.statusEvents = statusEvents;
        this.offers = offers;
        this.users = users;
        this.analyses = analyses;
        this.views = views;
    }

    public PageResponse<AdminRideSummaryResponse> search(RideStatus status, Instant from, Instant to, UUID passengerId,
                                                         UUID driverId, Pageable pageable) {
        Specification<Ride> filter = Specification.allOf(
                RideSpecifications.hasStatus(status),
                RideSpecifications.requestedFrom(from),
                RideSpecifications.requestedBefore(to),
                RideSpecifications.hasPassenger(passengerId),
                RideSpecifications.hasDriver(driverId));
        Page<Ride> page = rides.findAll(filter, pageable);
        List<RideSummaryResponse> summaries = views.toSummaries(page);
        List<AdminRideSummaryResponse> content = new ArrayList<>(summaries.size());
        for (int i = 0; i < summaries.size(); i++) {
            Ride ride = page.getContent().get(i);
            RideSummaryResponse summary = summaries.get(i);
            content.add(new AdminRideSummaryResponse(summary.id(), summary.status(), summary.vehicleCategory(),
                    ride.getPassengerId(), ride.getDriverId(), summary.pickupAddress(), summary.dropoffAddress(),
                    summary.fare(), summary.fareIsFinal(), summary.requestedAt(), summary.completedAt()));
        }
        return PageResponse.of(page, content);
    }

    public AdminRideDetailResponse detail(UUID rideId) {
        Ride ride = rides.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RIDE_NOT_FOUND, "Ride not found"));
        AdminRideDetailResponse.Passenger passenger = users.findById(ride.getPassengerId())
                .map(user -> new AdminRideDetailResponse.Passenger(user.getId(), user.getFullName(), user.getEmail()))
                .orElse(null);
        List<RideTimelineEntryResponse> timeline = statusEvents.findByRideIdOrderByIdAsc(rideId).stream()
                .map(event -> new RideTimelineEntryResponse(event.getFromStatus(), event.getToStatus(),
                        event.getActorType(), event.getReason(), event.getRideVersion(), event.getOccurredAt()))
                .toList();
        List<AdminRideDetailResponse.Offer> offerHistory = offers.findByRideIdOrderByOfferedAtAsc(rideId).stream()
                .map(offer -> new AdminRideDetailResponse.Offer(offer.getDriverId(), offer.getRound(),
                        offer.getDistanceMeters(), offer.getStatus(), offer.getOfferedAt(), offer.getExpiresAt(),
                        offer.getRespondedAt()))
                .toList();
        AdminRideDetailResponse.Analysis analysis = analyses.findByRideId(rideId)
                .map(row -> new AdminRideDetailResponse.Analysis(row.status(), row.failureCode(), row.updatedAt()))
                .orElse(null);
        return new AdminRideDetailResponse(views.toResponse(ride), passenger, timeline, offerHistory, analysis);
    }
}
