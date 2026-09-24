package com.rideflow.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rideflow.config.SecurityProperties;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.RefreshToken;
import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.UserStatus;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.repository.RefreshTokenRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(14);
    private static final String RAW = "raw-refresh-token";

    @Mock
    private RefreshTokenRepository repository;
    @Mock
    private AuditService auditService;

    private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("unused-secret-unused-secret-unused", "rideflow", Duration.ofMinutes(15)),
                new SecurityProperties.RefreshToken(TTL, Duration.ofDays(7), "rf_refresh", true, "Lax", "/api/auth"),
                4);
        service = new RefreshTokenService(repository, generator, auditService, Clock.fixed(NOW, ZoneOffset.UTC), properties);
        user = User.register("a@example.com", null, "{bcrypt}x", "A", Role.PASSENGER);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    private RefreshToken stored(UUID familyId, Instant expiresAt) {
        RefreshToken token = RefreshToken.issue(user, generator.hash(RAW), familyId, expiresAt);
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        return token;
    }

    private void saveAssignsIds() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
            return token;
        });
    }

    @Test
    void newFamilyStoresOnlyTheHashWithConfiguredExpiry() {
        saveAssignsIds();

        RefreshTokenService.IssuedRefreshToken issued = service.issueNewFamily(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "tokenHash")).isEqualTo(generator.hash(issued.rawToken()));
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(issued.rawToken()).isNotEqualTo(ReflectionTestUtils.getField(saved.getValue(), "tokenHash"));
    }

    @Test
    void rotationRevokesCurrentTokenAndKeepsFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken current = stored(familyId, NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(generator.hash(RAW))).thenReturn(Optional.of(current));
        saveAssignsIds();

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate(RAW);

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedById()).isEqualTo(rotated.token().id());
        assertThat(rotated.user()).isSameAs(user);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(familyId);
    }

    @Test
    void reusingRotatedTokenRevokesWholeFamilyAndAudits() {
        UUID familyId = UUID.randomUUID();
        RefreshToken alreadyRotated = stored(familyId, NOW.plus(Duration.ofDays(1)));
        alreadyRotated.rotateTo(UUID.randomUUID(), NOW.minusSeconds(60));
        when(repository.findByTokenHash(generator.hash(RAW))).thenReturn(Optional.of(alreadyRotated));

        assertThatThrownBy(() -> service.rotate(RAW))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.SESSION_REVOKED);
        verify(repository).revokeFamily(familyId, NOW);
        verify(auditService).record(eq(user.getId()), eq(AuditAction.REFRESH_TOKEN_REUSE_DETECTED), eq("USER"),
                eq(user.getId()), anyMap());
        verify(repository, never()).save(any());
    }

    @Test
    void tokenRevokedByLogoutIsRejectedWithoutReuseAlarm() {
        RefreshToken loggedOut = stored(UUID.randomUUID(), NOW.plus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(loggedOut, "revokedAt", NOW.minusSeconds(60));
        when(repository.findByTokenHash(generator.hash(RAW))).thenReturn(Optional.of(loggedOut));

        assertThatThrownBy(() -> service.rotate(RAW))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.SESSION_REVOKED);
        verify(repository, never()).revokeFamily(any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void expiredTokenIsRejected() {
        when(repository.findByTokenHash(generator.hash(RAW)))
                .thenReturn(Optional.of(stored(UUID.randomUUID(), NOW)));

        assertThatThrownBy(() -> service.rotate(RAW))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("never-issued"))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void suspendedUserCannotRefreshAndSessionIsRevoked() {
        UUID familyId = UUID.randomUUID();
        user.changeStatus(UserStatus.SUSPENDED);
        when(repository.findByTokenHash(generator.hash(RAW)))
                .thenReturn(Optional.of(stored(familyId, NOW.plus(Duration.ofDays(1)))));

        assertThatThrownBy(() -> service.rotate(RAW))
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
        verify(repository).revokeFamily(familyId, NOW);
    }
}
