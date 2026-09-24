package com.rideflow.controller;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.ride.BookRideRequest;
import com.rideflow.dto.ride.CancelRideRequest;
import com.rideflow.dto.ride.RideResponse;
import com.rideflow.dto.ride.RideSummaryResponse;
import com.rideflow.dto.ride.RideTimelineEntryResponse;
import com.rideflow.dto.ride.RideTrackingResponse;
import com.rideflow.entity.RideStatus;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.ride.RideBookingService;
import com.rideflow.service.ride.RideCancellationService;
import com.rideflow.service.ride.RideQueryService;
import com.rideflow.service.ride.RideTrackingService;
import com.rideflow.utility.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides")
@Tag(name = "Rides")
public class RideController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "requestedAt", "requestedAt",
            "completedAt", "completedAt");

    private final RideBookingService bookingService;
    private final RideQueryService queryService;
    private final RideCancellationService cancellationService;
    private final RideTrackingService trackingService;

    public RideController(RideBookingService bookingService, RideQueryService queryService,
                          RideCancellationService cancellationService, RideTrackingService trackingService) {
        this.bookingService = bookingService;
        this.queryService = queryService;
        this.cancellationService = cancellationService;
        this.trackingService = trackingService;
    }

    @PostMapping
    @Operation(summary = "Book a ride from a fare quote; matching starts asynchronously")
    public ResponseEntity<RideResponse> book(
            @AuthenticationPrincipal AuthenticatedUser passenger, @Valid @RequestBody BookRideRequest request) {
        RideResponse ride = bookingService.book(passenger.id(), request);
        return ResponseEntity.created(URI.create("/api/rides/" + ride.id())).body(ride);
    }

    @GetMapping
    @Operation(summary = "Trip history: rides booked (passenger) or driven (driver)")
    public PageResponse<RideSummaryResponse> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) RideStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "requestedAt,desc") String sort) {
        return queryService.history(user, status, PageRequests.of(page, size, sort, SORT_FIELDS));
    }

    @GetMapping("/active")
    @Operation(summary = "The caller's ongoing ride, or 204 if none (used to restore state after reload)")
    public ResponseEntity<RideResponse> active(@AuthenticationPrincipal AuthenticatedUser user) {
        return queryService.active(user).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{rideId}")
    public RideResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId) {
        return queryService.get(user, rideId);
    }

    @GetMapping("/{rideId}/timeline")
    @Operation(summary = "Status history of the ride")
    public List<RideTimelineEntryResponse> timeline(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId) {
        return queryService.timeline(user, rideId);
    }

    @GetMapping("/{rideId}/tracking")
    @Operation(summary = "Driver position and ETA snapshot; live updates then arrive over WebSocket")
    public RideTrackingResponse tracking(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID rideId) {
        return trackingService.snapshot(user, rideId);
    }

    @PostMapping("/{rideId}/cancel")
    @Operation(summary = "Passenger cancels; a driver before pickup releases the ride for re-dispatch")
    public RideResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID rideId,
            @Valid @RequestBody(required = false) CancelRideRequest request) {
        return cancellationService.cancel(user, rideId, request == null ? null : request.reason());
    }
}
