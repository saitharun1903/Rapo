package com.rideflow.controller;

import com.rideflow.dto.driver.DriverResponse;
import com.rideflow.dto.driver.GoOnlineRequest;
import com.rideflow.dto.driver.LocationUpdateRequest;
import com.rideflow.dto.driver.NearbyDriverResponse;
import com.rideflow.dto.ride.RideOfferResponse;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.driver.DriverAvailabilityService;
import com.rideflow.service.driver.DriverLocationService;
import com.rideflow.service.driver.NearbyDriverService;
import com.rideflow.service.matching.OfferQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Drivers")
public class DriverOperationsController {

    private final DriverAvailabilityService availabilityService;
    private final DriverLocationService locationService;
    private final NearbyDriverService nearbyDriverService;
    private final OfferQueryService offerQueryService;

    public DriverOperationsController(DriverAvailabilityService availabilityService,
                                      DriverLocationService locationService,
                                      NearbyDriverService nearbyDriverService,
                                      OfferQueryService offerQueryService) {
        this.availabilityService = availabilityService;
        this.locationService = locationService;
        this.nearbyDriverService = nearbyDriverService;
        this.offerQueryService = offerQueryService;
    }

    @PostMapping("/online")
    @Operation(summary = "Go online at the given position")
    public DriverResponse goOnline(
            @AuthenticationPrincipal AuthenticatedUser driver, @Valid @RequestBody GoOnlineRequest request) {
        return availabilityService.goOnline(driver.id(), request.location());
    }

    @PostMapping("/offline")
    @Operation(summary = "Go offline; any pending offer is released")
    public DriverResponse goOffline(@AuthenticationPrincipal AuthenticatedUser driver) {
        return availabilityService.goOffline(driver.id());
    }

    @PostMapping("/location")
    @Operation(summary = "Report the current GPS position (REST fallback for the WebSocket stream)")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reportLocation(
            @AuthenticationPrincipal AuthenticatedUser driver, @Valid @RequestBody LocationUpdateRequest request) {
        locationService.report(driver.id(), request);
    }

    @GetMapping("/me/offers")
    @Operation(summary = "Open ride offers for the current driver")
    public List<RideOfferResponse> openOffers(@AuthenticationPrincipal AuthenticatedUser driver) {
        return offerQueryService.openOffers(driver.id());
    }

    @GetMapping("/nearby")
    @Operation(summary = "Available cars near a point (coarsened and anonymous for passengers)")
    public List<NearbyDriverResponse> nearby(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam(defaultValue = "3000") @Min(100) @Max(10000) int radiusMeters,
            @RequestParam(required = false) VehicleCategory category) {
        return nearbyDriverService.find(user.role(), new GeoPoint(lat, lng), radiusMeters, category);
    }
}
