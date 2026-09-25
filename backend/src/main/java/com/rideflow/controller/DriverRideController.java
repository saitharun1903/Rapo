package com.rideflow.controller;

import com.rideflow.dto.ride.RideResponse;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.ride.DriverRideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Driver-side ride lifecycle. Every transition is validated by the ride state machine. */
@RestController
@RequestMapping("/api/rides/{rideId}")
@Tag(name = "Rides: driver actions")
public class DriverRideController {

    private final DriverRideService driverRideService;

    public DriverRideController(DriverRideService driverRideService) {
        this.driverRideService = driverRideService;
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept an open offer (first driver to accept wins)")
    public RideResponse accept(@AuthenticationPrincipal AuthenticatedUser driver, @PathVariable UUID rideId) {
        return driverRideService.accept(driver.id(), rideId);
    }

    @PostMapping("/reject")
    @Operation(summary = "Decline an open offer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AuthenticatedUser driver, @PathVariable UUID rideId) {
        driverRideService.reject(driver.id(), rideId);
    }

    @PostMapping("/en-route")
    @Operation(summary = "Start driving to the pickup")
    public RideResponse enRoute(@AuthenticationPrincipal AuthenticatedUser driver, @PathVariable UUID rideId) {
        return driverRideService.markEnRoute(driver.id(), rideId);
    }

    @PostMapping("/arrive")
    @Operation(summary = "Arrived at pickup (requires a fresh location inside the pickup geofence)")
    public RideResponse arrive(@AuthenticationPrincipal AuthenticatedUser driver, @PathVariable UUID rideId) {
        return driverRideService.markArrived(driver.id(), rideId);
    }

    @PostMapping("/start")
    @Operation(summary = "Passenger on board; the trip starts")
    public RideResponse start(@AuthenticationPrincipal AuthenticatedUser driver, @PathVariable UUID rideId) {
        return driverRideService.start(driver.id(), rideId);
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete the trip; measures distance from the GPS trail and computes the final fare")
    public RideResponse complete(@AuthenticationPrincipal AuthenticatedUser driver, @PathVariable UUID rideId) {
        return driverRideService.complete(driver.id(), rideId);
    }
}
