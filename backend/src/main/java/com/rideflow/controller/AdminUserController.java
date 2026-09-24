package com.rideflow.controller;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.dto.user.UserStatusUpdateRequest;
import com.rideflow.entity.Role;
import com.rideflow.entity.UserStatus;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.user.UserAdministrationService;
import com.rideflow.utility.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin: users")
public class AdminUserController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "fullName", "fullName",
            "email", "email",
            "lastLoginAt", "lastLoginAt");

    private final UserAdministrationService administrationService;

    public AdminUserController(UserAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping
    @Operation(summary = "Search users by role, status and name/email text")
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return administrationService.search(role, status, q, PageRequests.of(page, size, sort, SORT_FIELDS));
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Suspend or reactivate a passenger or driver account")
    public UserResponse changeStatus(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID userId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return administrationService.changeStatus(admin.id(), userId, request.status(), request.reason());
    }
}
