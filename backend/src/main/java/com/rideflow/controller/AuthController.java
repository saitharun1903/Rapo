package com.rideflow.controller;

import com.rideflow.dto.auth.AuthResponse;
import com.rideflow.dto.auth.LoginRequest;
import com.rideflow.dto.auth.RegisterRequest;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.exception.AuthenticationFailedException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.security.RefreshTokenCookies;
import com.rideflow.service.auth.AuthService;
import com.rideflow.service.auth.AuthSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    public AuthController(AuthService authService, RefreshTokenCookies refreshTokenCookies) {
        this.authService = authService;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a passenger or driver account")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, http.getRemoteAddr()));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in; returns an access token and sets the refresh-token cookie")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withRefreshCookie(authService.login(request, http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh-token cookie and issue a new access token",
            description = "Requires header X-Requested-With: rideflow")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        refreshTokenCookies.requireCsrfHeader(request);
        String refreshToken = refreshTokenCookies.read(request)
                .orElseThrow(() -> new AuthenticationFailedException(ErrorCode.UNAUTHENTICATED, "No refresh token"));
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current session and clear the refresh-token cookie",
            description = "Requires header X-Requested-With: rideflow")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        refreshTokenCookies.requireCsrfHeader(request);
        refreshTokenCookies.read(request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.issue(session.refreshToken()).toString())
                .body(session.response());
    }
}
