package com.rideflow.controller;

import com.rideflow.dto.geo.PlaceResponse;
import com.rideflow.dto.ride.RouteResponse;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.RouteEstimate;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.geo.GeocodingService;
import com.rideflow.service.geo.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geo")
@Tag(name = "Geo")
public class GeoController {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 200;

    private final RouteService routeService;
    private final GeocodingService geocodingService;

    public GeoController(RouteService routeService, GeocodingService geocodingService) {
        this.routeService = routeService;
        this.geocodingService = geocodingService;
    }

    @GetMapping("/route")
    @Operation(summary = "Road route between two points (straight-line fallback flagged APPROXIMATE)")
    public RouteResponse route(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double fromLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng) {
        RouteEstimate route = routeService.route(new GeoPoint(fromLat, fromLng), new GeoPoint(toLat, toLng));
        return new RouteResponse(route.distanceMeters(), route.durationSeconds(), route.source(), route.path());
    }

    @GetMapping("/search")
    @Operation(summary = "Place search, biased towards lat/lng when given; call on submit, not per keystroke")
    public List<PlaceResponse> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @NotBlank @Size(min = MIN_QUERY_LENGTH, max = MAX_QUERY_LENGTH) String q,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lng) {
        GeoPoint near = lat == null || lng == null ? null : new GeoPoint(lat, lng);
        return geocodingService.search(user.id(), q, near);
    }

    @GetMapping("/reverse")
    @Operation(summary = "Address at a point, or 204 if there is none (for example at sea)")
    public ResponseEntity<PlaceResponse> reverse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng) {
        return geocodingService.reverse(user.id(), new GeoPoint(lat, lng))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
