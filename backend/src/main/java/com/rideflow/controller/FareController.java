package com.rideflow.controller;

import com.rideflow.dto.fare.FareEstimateRequest;
import com.rideflow.dto.fare.FareEstimateResponse;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.fare.FareQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fares")
@Tag(name = "Fares")
public class FareController {

    private final FareQuoteService quoteService;

    public FareController(FareQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping("/estimate")
    @Operation(summary = "Route, surge and a signed fare quote per vehicle category")
    public FareEstimateResponse estimate(
            @AuthenticationPrincipal AuthenticatedUser passenger, @Valid @RequestBody FareEstimateRequest request) {
        return quoteService.estimate(passenger.id(), request.pickup(), request.dropoff());
    }
}
