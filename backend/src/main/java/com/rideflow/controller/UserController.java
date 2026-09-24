package com.rideflow.controller;

import com.rideflow.dto.user.ChangePasswordRequest;
import com.rideflow.dto.user.UpdateProfileRequest;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.security.AuthenticatedUser;
import com.rideflow.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Current user's profile")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.getProfile(user.id());
    }

    @PatchMapping
    @Operation(summary = "Update name and phone")
    public UserResponse update(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(user.id(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change password; signs out all sessions")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request);
        return ResponseEntity.noContent().build();
    }
}
