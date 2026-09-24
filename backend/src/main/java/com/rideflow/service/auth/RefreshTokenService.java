package com.rideflow.service.auth;

import com.rideflow.config.SecurityProperties;
import com.rideflow.entity.AuditAction;
import com.rideflow.entity.RefreshToken;
import com.rideflow.entity.User;
import com.rideflow.exception.AuthenticationFailedException;
import com.rideflow.exception.ErrorCode;
import com.rideflow.repository.RefreshTokenRepository;
import com.rideflow.security.OpaqueTokenGenerator;
import com.rideflow.service.audit.AuditService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token rotation with reuse detection. Must run inside the caller's transaction; failures do not
 * mark it rollback-only, because revoking a compromised family has to be committed even though the
 * request itself is rejected.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = AuthenticationFailedException.class)
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String ENTITY_TYPE = "USER";

    private final RefreshTokenRepository refreshTokens;
    private final OpaqueTokenGenerator tokenGenerator;
    private final AuditService auditService;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokens,
            OpaqueTokenGenerator tokenGenerator,
            AuditService auditService,
            Clock clock,
            SecurityProperties properties) {
        this.refreshTokens = refreshTokens;
        this.tokenGenerator = tokenGenerator;
        this.auditService = auditService;
        this.clock = clock;
        this.ttl = properties.refreshToken().ttl();
    }

    /** Starts a new rotation family (a new login session). */
    public IssuedRefreshToken issueNewFamily(User user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * Exchanges a valid refresh token for a new one in the same family. Presenting a token that was
     * already rotated means it leaked (or a client raced itself), so the entire family is revoked.
     */
    public RotatedRefreshToken rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> invalid("Refresh token is not recognised"));

        if (current.isRevoked() && !current.wasRotated()) {
            // Ended deliberately (logout, password change, suspension): not an attack, nothing more to revoke.
            throw new AuthenticationFailedException(ErrorCode.SESSION_REVOKED,
                    "Session has ended; please sign in again");
        }
        if (current.isRevoked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            UUID userId = current.getUser().getId();
            log.warn("Refresh token reuse detected for user {}; family {} revoked", userId, current.getFamilyId());
            auditService.record(userId, AuditAction.REFRESH_TOKEN_REUSE_DETECTED, ENTITY_TYPE, userId,
                    Map.of("familyId", current.getFamilyId().toString()));
            throw new AuthenticationFailedException(ErrorCode.SESSION_REVOKED,
                    "Session was revoked because a refresh token was reused; please sign in again");
        }
        if (current.isExpired(now)) {
            throw invalid("Refresh token has expired");
        }
        User user = current.getUser();
        if (!user.isActive()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new AuthenticationFailedException(ErrorCode.ACCOUNT_SUSPENDED, "Account is suspended");
        }

        IssuedRefreshToken next = issue(user, current.getFamilyId());
        current.rotateTo(next.id(), now);
        return new RotatedRefreshToken(user, next);
    }

    /** Revokes the session the token belongs to. Unknown tokens are ignored so logout is idempotent. */
    public void revokeFamilyOf(String rawToken) {
        Optional<RefreshToken> token = refreshTokens.findByTokenHash(tokenGenerator.hash(rawToken));
        token.ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    public void revokeAllForUser(UUID userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    private IssuedRefreshToken issue(User user, UUID familyId) {
        String raw = tokenGenerator.generate();
        RefreshToken token = refreshTokens.save(
                RefreshToken.issue(user, tokenGenerator.hash(raw), familyId, clock.instant().plus(ttl)));
        return new IssuedRefreshToken(token.getId(), raw);
    }

    private static AuthenticationFailedException invalid(String message) {
        return new AuthenticationFailedException(ErrorCode.INVALID_TOKEN, message);
    }

    public record IssuedRefreshToken(UUID id, String rawToken) {

        @Override
        public String toString() {
            return "IssuedRefreshToken[id=" + id + "]";
        }
    }

    public record RotatedRefreshToken(User user, IssuedRefreshToken token) {
    }
}
