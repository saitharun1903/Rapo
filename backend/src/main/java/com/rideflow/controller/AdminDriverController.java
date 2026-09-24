package com.rideflow.controller;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.driver.DriverDecisionRequest;
import com.rideflow.dto.driver.DriverResponse;
import com.rideflow.entity.DriverVerificationStatus;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.driver.DriverAdministrationService;
import com.rideflow.utility.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/drivers")
@Tag(name = "Admin: drivers")
public class AdminDriverController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "verifiedAt", "verifiedAt",
            "ratingAvg", "ratingAvg");

    private final DriverAdministrationService administrationService;

    public AdminDriverController(DriverAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping
    @Operation(summary = "List drivers, optionally filtered by verification status")
    public PageResponse<DriverResponse> list(
            @RequestParam(required = false) DriverVerificationStatus verificationStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,asc") String sort) {
        return administrationService.list(verificationStatus, PageRequests.of(page, size, sort, SORT_FIELDS));
    }

    @PostMapping("/{driverId}/verify")
    @Operation(summary = "Approve a driver so they can go online")
    public DriverResponse verify(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID driverId) {
        return administrationService.verify(admin.id(), driverId);
    }

    @PostMapping("/{driverId}/reject")
    @Operation(summary = "Reject a pending driver application")
    public DriverResponse reject(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverDecisionRequest request) {
        return administrationService.reject(admin.id(), driverId, request.reason());
    }

    @PostMapping("/{driverId}/suspend")
    @Operation(summary = "Suspend a verified driver and force them offline")
    public DriverResponse suspend(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverDecisionRequest request) {
        return administrationService.suspend(admin.id(), driverId, request.reason());
    }
}
