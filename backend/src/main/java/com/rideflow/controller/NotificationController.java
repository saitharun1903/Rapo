package com.rideflow.controller;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.notification.NotificationResponse;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.notification.NotificationService;
import com.rideflow.utility.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller's in-app notifications; new ones are also pushed to {@code /user/queue/notifications}. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private static final Map<String, String> SORT_FIELDS = Map.of("createdAt", "createdAt");

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Newest first by default")
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return notificationService.list(user.id(), unreadOnly, PageRequests.of(page, size, sort, SORT_FIELDS));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID notificationId) {
        notificationService.markRead(user.id(), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        notificationService.markAllRead(user.id());
    }
}
