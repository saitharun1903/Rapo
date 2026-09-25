package com.rideflow.controller;

import com.rideflow.dto.common.ReportGranularity;
import com.rideflow.dto.driver.EarningsResponse;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.driver.DriverEarningsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/earnings")
@Tag(name = "Drivers")
public class DriverEarningsController {

    private final DriverEarningsService earningsService;

    public DriverEarningsController(DriverEarningsService earningsService) {
        this.earningsService = earningsService;
    }

    @GetMapping
    @Operation(summary = "Earnings from rides completed in [from, to), bucketed by hour or day")
    public EarningsResponse earnings(
            @AuthenticationPrincipal AuthenticatedUser driver,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity) {
        return earningsService.earnings(driver.id(), from, to, granularity);
    }
}
