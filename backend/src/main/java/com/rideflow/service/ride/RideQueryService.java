package com.rideflow.service.ride;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.ride.RideResponse;
import com.rideflow.dto.ride.RideSummaryResponse;
import com.rideflow.dto.ride.RideTimelineEntryResponse;
import com.rideflow.entity.Ride;
import com.rideflow.entity.RideStatus;
import com.rideflow.entity.Role;
import com.rideflow.repository.RideRepository;
import com.rideflow.repository.RideStatusEventRepository;
import com.rideflow.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RideQueryService {

    private final RideRepository rides;
    private final RideStatusEventRepository statusEvents;
    private final RideAccessPolicy access;
    private final RideViewAssembler views;

    public RideQueryService(RideRepository rides, RideStatusEventRepository statusEvents, RideAccessPolicy access,
                            RideViewAssembler views) {
        this.rides = rides;
        this.statusEvents = statusEvents;
        this.access = access;
        this.views = views;
    }

    public RideResponse get(AuthenticatedUser user, UUID rideId) {
        return views.toResponse(access.loadVisible(user, rideId));
    }

    /**
     * The ride as its participants see it, without an access check. Only for server-side pushes, whose
     * recipients must be taken from the returned view (its passenger and current driver).
     */
    public Optional<RideResponse> findForParticipants(UUID rideId) {
        return rides.findById(rideId).map(views::toResponse);
    }

    /** The caller's current ride: used by clients after reload or reconnect to restore state. */
    public Optional<RideResponse> active(AuthenticatedUser user) {
        Optional<Ride> ride = user.role() == Role.DRIVER
                ? rides.findFirstByDriverIdAndStatusIn(user.id(), RideStatus.DRIVER_ENGAGED)
                : rides.findFirstByPassengerIdAndStatusIn(user.id(), RideStatus.ACTIVE);
        return ride.map(views::toResponse);
    }

    /** Passengers see rides they booked; drivers see rides they were assigned. */
    public PageResponse<RideSummaryResponse> history(AuthenticatedUser user, RideStatus status, Pageable pageable) {
        Page<Ride> page = switch (user.role()) {
            case DRIVER -> status == null ? rides.findByDriverId(user.id(), pageable)
                    : rides.findByDriverIdAndStatus(user.id(), status, pageable);
            case PASSENGER, ADMIN -> status == null ? rides.findByPassengerId(user.id(), pageable)
                    : rides.findByPassengerIdAndStatus(user.id(), status, pageable);
        };
        return PageResponse.of(page, views.toSummaries(page));
    }

    public List<RideTimelineEntryResponse> timeline(AuthenticatedUser user, UUID rideId) {
        access.loadVisible(user, rideId);
        return statusEvents.findByRideIdOrderByIdAsc(rideId).stream()
                .map(event -> new RideTimelineEntryResponse(event.getFromStatus(), event.getToStatus(),
                        event.getActorType(), event.getReason(), event.getRideVersion(), event.getOccurredAt()))
                .toList();
    }
}
