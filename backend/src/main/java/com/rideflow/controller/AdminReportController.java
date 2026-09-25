package com.rideflow.controller;

import com.rideflow.dto.admin.AdminOverviewResponse;
import com.rideflow.dto.admin.RideActivityResponse;
import com.rideflow.dto.admin.SystemStatusResponse;
import com.rideflow.dto.common.ReportGranularity;
import com.rideflow.service.admin.AdminReportService;
import com.rideflow.service.admin.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin: reports")
public class AdminReportController {

    private final AdminReportService reportService;
    private final SystemStatusService systemStatusService;

    public AdminReportController(AdminReportService reportService, SystemStatusService systemStatusService) {
        this.reportService = reportService;
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Ride counts, rates, time to match and revenue for [from, to); drivers by availability now")
    public AdminOverviewResponse overview(@RequestParam Instant from, @RequestParam Instant to) {
        return reportService.overview(from, to);
    }

    @GetMapping("/analytics/rides")
    @Operation(summary = "Requested, completed, cancelled and expired rides and revenue per hour or day")
    public RideActivityResponse rideActivity(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity) {
        return reportService.rideActivity(from, to, granularity);
    }

    @GetMapping("/system")
    @Operation(summary = "Health, outbox backlog, dead letters, AI circuit and WebSocket sessions of this instance")
    public SystemStatusResponse system() {
        return systemStatusService.status();
    }
}
