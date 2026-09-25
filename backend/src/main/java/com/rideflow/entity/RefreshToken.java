package com.rideflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A single refresh token in a rotation chain. Only the SHA-256 hash is stored. Tokens issued from the
 * same login share a {@code familyId}; presenting an already-rotated token revokes the whole family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, String tokenHash, UUID familyId, Instant expiresAt) {
        return new RefreshToken(user, tokenHash, familyId, expiresAt);
    }

    public void rotateTo(UUID successorId, Instant at) {
        this.revokedAt = at;
        this.replacedById = successorId;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** True if this token was exchanged for a successor, as opposed to revoked by logout or an admin. */
    public boolean wasRotated() {
        return replacedById != null;
    }

    /** True if this token was exchanged for a successor no longer than {@code grace} before {@code now}. */
    public boolean rotatedWithin(Duration grace, Instant now) {
        return wasRotated() && !now.isAfter(revokedAt.plus(grace));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RefreshToken token && id != null && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(RefreshToken.class);
    }
}
