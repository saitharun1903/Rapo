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
    private final Duration reuseGrace;

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
        this.reuseGrace = properties.refreshToken().reuseGrace();
    }

    /** Starts a new rotation family (a new login session). */
    public IssuedRefreshToken issueNewFamily(User user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * Exchanges a valid refresh token for a new one in the same family. Presenting a token that was already
     * rotated means it leaked, so the entire family is revoked, with one exception: the client that lost the
     * response. A reload or dropped connection during a refresh leaves the browser with the old cookie, while the
     * server has already rotated it; if that old token comes back within the reuse grace and its successor has
     * never been presented, the successor (which never reached anyone) is replaced instead. A thief replaying a
     * token after the rightful client has used its successor is still caught.
     */
    public RotatedRefreshToken rotate(String rawToken) {
        Instant now = clock.instant();
        // Locked, so concurrent requests with the same token are decided one after the other.
        RefreshToken current = refreshTokens.findForRotation(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> invalid("Refresh token is not recognised"));

        if (current.isRevoked() && !current.wasRotated()) {
            // Ended deliberately (logout, password change, suspension): not an attack, nothing more to revoke.
            throw new AuthenticationFailedException(ErrorCode.SESSION_REVOKED,
                    "Session has ended; please sign in again");
        }
        RefreshToken toRotate = current.isRevoked() ? lostSuccessor(current, now) : current;
        if (toRotate.isExpired(now)) {
            throw invalid("Refresh token has expired");
        }
        User user = toRotate.getUser();
        if (!user.isActive()) {
            refreshTokens.revokeFamily(toRotate.getFamilyId(), now);
            throw new AuthenticationFailedException(ErrorCode.ACCOUNT_SUSPENDED, "Account is suspended");
        }

        IssuedRefreshToken next = issue(user, toRotate.getFamilyId());
        toRotate.rotateTo(next.id(), now);
        return new RotatedRefreshToken(user, next);
    }

    /**
     * For a rotated token presented again: its successor, if the client can only have lost it (rotated within the
     * grace and never presented since). Anything else is reuse, and revokes the family.
     */
    private RefreshToken lostSuccessor(RefreshToken rotated, Instant now) {
        if (rotated.rotatedWithin(reuseGrace, now)) {
            RefreshToken successor = refreshTokens.findByIdForRotation(rotated.getReplacedById()).orElse(null);
            if (successor != null && !successor.isRevoked()) {
                log.info("Refresh token of family {} presented again within the grace period; replacing its "
                        + "unused successor", rotated.getFamilyId());
                return successor;
            }
        }
        refreshTokens.revokeFamily(rotated.getFamilyId(), now);
        UUID userId = rotated.getUser().getId();
        log.warn("Refresh token reuse detected for user {}; family {} revoked", userId, rotated.getFamilyId());
        auditService.record(userId, AuditAction.REFRESH_TOKEN_REUSE_DETECTED, ENTITY_TYPE, userId,
                Map.of("familyId", rotated.getFamilyId().toString()));
        throw new AuthenticationFailedException(ErrorCode.SESSION_REVOKED,
                "Session was revoked because a refresh token was reused; please sign in again");
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
