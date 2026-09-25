package com.rideflow.controller;

import com.rideflow.dto.admin.AuditLogResponse;
import com.rideflow.dto.common.PageResponse;
import com.rideflow.entity.AuditAction;
import com.rideflow.service.audit.AuditService;
import com.rideflow.utility.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@Tag(name = "Admin: audit log")
public class AdminAuditLogController {

    private static final Map<String, String> SORT_FIELDS = Map.of("createdAt", "createdAt");

    private final AuditService auditService;

    public AdminAuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Security and administrative actions, newest first by default")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @Size(max = 40) String entityType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return auditService.search(action, entityType, from, to, PageRequests.of(page, size, sort, SORT_FIELDS));
    }
}
