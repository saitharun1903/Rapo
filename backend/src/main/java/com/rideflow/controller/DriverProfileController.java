package com.rideflow.controller;

import com.rideflow.dto.driver.DriverProfileRequest;
import com.rideflow.dto.driver.DriverResponse;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.driver.DriverOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me")
@Tag(name = "Drivers")
public class DriverProfileController {

    private final DriverOnboardingService onboardingService;

    public DriverProfileController(DriverOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/profile")
    @Operation(summary = "Submit licence and vehicle for verification")
    public ResponseEntity<DriverResponse> submitProfile(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody DriverProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(onboardingService.submitProfile(user.id(), request));
    }

    @GetMapping
    @Operation(summary = "Current driver's profile, verification status and vehicle")
    public DriverResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return onboardingService.getProfile(user.id());
    }
}
