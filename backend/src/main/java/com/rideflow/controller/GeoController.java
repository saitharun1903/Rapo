package com.rideflow.controller;

import com.rideflow.dto.ride.RouteResponse;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.RouteEstimate;
import com.rideflow.geospatial.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geo")
@Tag(name = "Geo")
public class GeoController {

    private final RoutingService routingService;

    public GeoController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/route")
    @Operation(summary = "Road route between two points (straight-line fallback flagged APPROXIMATE)")
    public RouteResponse route(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double fromLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng) {
        RouteEstimate route = routingService.route(new GeoPoint(fromLat, fromLng), new GeoPoint(toLat, toLng));
        return new RouteResponse(route.distanceMeters(), route.durationSeconds(), route.source(), route.path());
    }
}
