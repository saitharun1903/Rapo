package com.rideflow.entity;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An offer of a ride to one driver during a matching round. Offers are inserted with a native
 * {@code ON CONFLICT DO NOTHING} statement (see RideOfferRepository) so that the "one pending offer per
 * driver" unique index resolves races between concurrent matching rounds.
 */
@Entity
@Table(name = "ride_offers")
public class RideOffer {

    @Id
    private UUID id;

    @Column(name = "ride_id", nullable = false, updatable = false)
    private UUID rideId;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "round", nullable = false, updatable = false)
    private short round;

    @Column(name = "distance_m", nullable = false, updatable = false)
    private int distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OfferStatus status;

    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected RideOffer() {
    }

    public boolean isOpen(Instant now) {
        return status == OfferStatus.PENDING && now.isBefore(expiresAt);
    }

    public void accept(Instant now) {
        requireOpen(now);
        this.status = OfferStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void reject(Instant now) {
        requireOpen(now);
        this.status = OfferStatus.REJECTED;
        this.respondedAt = now;
    }

    /** The driver backed out after accepting; frees the ride's single "accepted" slot for the next driver. */
    public void withdraw(Instant now) {
        if (status == OfferStatus.ACCEPTED) {
            this.status = OfferStatus.CANCELLED;
            this.respondedAt = now;
        }
    }

    private void requireOpen(Instant now) {
        if (!isOpen(now)) {
            throw new InvalidStateException(ErrorCode.OFFER_EXPIRED, "This ride offer is no longer available");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public int getRound() {
        return round;
    }

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public Instant getOfferedAt() {
        return offeredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RideOffer offer && id != null && id.equals(offer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(RideOffer.class);
    }
}
