package com.rideflow.controller;

import com.rideflow.dto.admin.AdminRideDetailResponse;
import com.rideflow.dto.admin.AdminRideSummaryResponse;
import com.rideflow.dto.common.PageResponse;
import com.rideflow.entity.RideStatus;
import com.rideflow.service.admin.AdminRideService;
import com.rideflow.utility.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rides")
@Tag(name = "Admin: rides")
public class AdminRideController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "requestedAt", "requestedAt",
            "completedAt", "completedAt");

    private final AdminRideService rideService;

    public AdminRideController(AdminRideService rideService) {
        this.rideService = rideService;
    }

    @GetMapping
    @Operation(summary = "Search all rides by status, request time, passenger or driver")
    public PageResponse<AdminRideSummaryResponse> search(
            @RequestParam(required = false) RideStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID passengerId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "requestedAt,desc") String sort) {
        return rideService.search(status, from, to, passengerId, driverId,
                PageRequests.of(page, size, sort, SORT_FIELDS));
    }

    @GetMapping("/{rideId}")
    @Operation(summary = "A ride with its passenger, status history, offers and AI analysis status")
    public AdminRideDetailResponse detail(@PathVariable UUID rideId) {
        return rideService.detail(rideId);
    }
}
