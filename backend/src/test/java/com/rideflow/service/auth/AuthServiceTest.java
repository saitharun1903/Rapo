package com.rideflow.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.cache.RateLimitScope;
import com.rideflow.cache.RateLimiter;
import com.rideflow.dto.auth.AccountType;
import com.rideflow.dto.auth.LoginRequest;
import com.rideflow.dto.auth.RegisterRequest;
import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.UserStatus;
import com.rideflow.exception.AuthenticationFailedException;
import com.rideflow.exception.DuplicateResourceException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RetryableException;
import com.rideflow.exception.RideFlowException;
import com.rideflow.mapper.UserMapper;
import com.rideflow.repository.UserRepository;
import com.rideflow.security.AccessTokenService;
import com.rideflow.security.OpaqueTokenGenerator;
import com.rideflow.service.audit.AuditService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final String PASSWORD = "Correct-horse-9";
    private static final String CLIENT_IP = "203.0.113.7";

    @Mock
    private UserRepository users;
    @Mock
    private AccessTokenService accessTokens;
    @Mock
    private RefreshTokenService refreshTokens;
    @Mock
    private AuditService auditService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RateLimiter rateLimiter;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, passwordEncoder, accessTokens, refreshTokens, auditService, userMapper,
                new OpaqueTokenGenerator(), rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User existingUser(UserStatus status) {
        User user = User.register("asha@example.com", null, passwordEncoder.encode(PASSWORD), "Asha", Role.PASSENGER);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.changeStatus(status);
        return user;
    }

    @Test
    void registerNormalisesEmailHashesPasswordAndAssignsRole() {
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any())).thenReturn(null);

        authService.register(new RegisterRequest(" Asha@Example.com ", PASSWORD, " Asha Rao ", " ", AccountType.DRIVER),
                CLIENT_IP);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Asha Rao");
        assertThat(saved.getValue().getPhone()).isNull();
        assertThat(saved.getValue().getRole()).isEqualTo(Role.DRIVER);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("asha@example.com", PASSWORD, "Asha", null, AccountType.PASSENGER), CLIENT_IP))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.EMAIL_TAKEN);
        verify(users, never()).save(any());
    }

    @Test
    void registerRejectsDuplicatePhone() {
        when(users.existsByPhone("+919876543210")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("new@example.com", PASSWORD, "Asha", "+919876543210", AccountType.PASSENGER),
                CLIENT_IP))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.PHONE_TAKEN);
    }

    @Test
    void loginIsRateLimitedPerIpAndNormalisedEmailBeforeThePasswordIsChecked() {
        doThrow(new RetryableException(ErrorCode.RATE_LIMITED, "Too many requests", Duration.ofSeconds(30)))
                .when(rateLimiter).acquire(RateLimitScope.LOGIN, CLIENT_IP + "|asha@example.com");

        assertThatThrownBy(() -> authService.login(new LoginRequest(" ASHA@example.com ", PASSWORD), CLIENT_IP))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        verify(users, never()).findByEmail(any());
    }

    @Test
    void registrationIsRateLimitedPerIp() {
        doThrow(new RetryableException(ErrorCode.RATE_LIMITED, "Too many requests", Duration.ofSeconds(30)))
                .when(rateLimiter).acquire(RateLimitScope.REGISTER, CLIENT_IP);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("new@example.com", PASSWORD, "Asha", null, AccountType.PASSENGER), CLIENT_IP))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        verify(users, never()).save(any());
    }

    @Test
    void loginWithUnknownEmailFailsGenericallyWithoutAudit() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD), CLIENT_IP))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Email or password is incorrect");
        verifyNoInteractions(auditService, refreshTokens, accessTokens);
    }

    @Test
    void loginWithWrongPasswordFailsAndIsAudited() {
        User user = existingUser(UserStatus.ACTIVE);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ASHA@example.com", "Wrong-password-1"), CLIENT_IP))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(auditService).recordIndependently(isNull(), eq(AuditAction.LOGIN_FAILED), eq("USER"), eq(user.getId()), anyMap());
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void suspendedUserWithCorrectPasswordIsRefused() {
        User user = existingUser(UserStatus.SUSPENDED);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", PASSWORD), CLIENT_IP))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void successfulLoginIssuesBothTokensAndRecordsLoginTime() {
        User user = existingUser(UserStatus.ACTIVE);
        UserResponse userResponse = new UserResponse(user.getId(), user.getEmail(), null, "Asha", Role.PASSENGER,
                UserStatus.ACTIVE, NOW);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(accessTokens.issue(user)).thenReturn(new AccessTokenService.IssuedAccessToken("jwt", Duration.ofMinutes(15)));
        when(refreshTokens.issueNewFamily(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken(UUID.randomUUID(), "raw-refresh"));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        AuthSession session = authService.login(new LoginRequest("asha@example.com", PASSWORD), CLIENT_IP);

        assertThat(session.response().accessToken()).isEqualTo("jwt");
        assertThat(session.response().tokenType()).isEqualTo("Bearer");
        assertThat(session.response().expiresIn()).isEqualTo(900);
        assertThat(session.response().user()).isEqualTo(userResponse);
        assertThat(session.refreshToken()).isEqualTo("raw-refresh");
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }
}
