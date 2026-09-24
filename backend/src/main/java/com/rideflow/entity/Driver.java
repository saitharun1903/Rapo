package com.rideflow.entity;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Driver profile. Shares its primary key with {@link User}, so a driver's id equals their user id
 * everywhere (tokens, WebSocket principal, APIs).
 */
@Entity
@Table(name = "drivers")
public class Driver extends TimestampedEntity {

    private static final Set<DriverVerificationStatus> VERIFIABLE_FROM = EnumSet.of(
            DriverVerificationStatus.PENDING, DriverVerificationStatus.REJECTED, DriverVerificationStatus.SUSPENDED);

    @Id
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private User user;

    @Column(name = "license_number", nullable = false, length = 40)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16)
    private DriverVerificationStatus verificationStatus;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 16)
    private DriverAvailability availability;

    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Driver() {
    }

    private Driver(User user, String licenseNumber) {
        this.user = user;
        this.licenseNumber = licenseNumber;
        this.verificationStatus = DriverVerificationStatus.PENDING;
        this.availability = DriverAvailability.OFFLINE;
    }

    public static Driver onboard(User user, String licenseNumber) {
        return new Driver(user, licenseNumber);
    }

    public void verify(UUID adminId, Instant at) {
        requireStatus(VERIFIABLE_FROM, "verify");
        this.verificationStatus = DriverVerificationStatus.VERIFIED;
        this.verifiedAt = at;
        this.verifiedBy = adminId;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        requireStatus(EnumSet.of(DriverVerificationStatus.PENDING), "reject");
        this.verificationStatus = DriverVerificationStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void suspend(String reason) {
        if (availability == DriverAvailability.ON_TRIP) {
            throw new InvalidStateException(ErrorCode.INVALID_DRIVER_STATE,
                    "Cannot suspend a driver who is currently on a trip");
        }
        requireStatus(EnumSet.of(DriverVerificationStatus.VERIFIED), "suspend");
        this.verificationStatus = DriverVerificationStatus.SUSPENDED;
        this.rejectionReason = reason;
        this.availability = DriverAvailability.OFFLINE;
    }

    /** Idempotent: an already-available driver stays available. */
    public void goOnline() {
        if (verificationStatus != DriverVerificationStatus.VERIFIED) {
            throw new InvalidStateException(ErrorCode.DRIVER_NOT_VERIFIED,
                    "Driver must be verified before going online (status: " + verificationStatus + ")");
        }
        if (availability == DriverAvailability.OFFLINE) {
            availability = DriverAvailability.AVAILABLE;
        }
    }

    public void goOffline() {
        if (availability == DriverAvailability.ON_TRIP) {
            throw new InvalidStateException(ErrorCode.DRIVER_ON_TRIP, "Finish or cancel the current trip before going offline");
        }
        availability = DriverAvailability.OFFLINE;
    }

    public void startTrip() {
        if (availability != DriverAvailability.AVAILABLE) {
            throw new InvalidStateException(ErrorCode.DRIVER_UNAVAILABLE,
                    "Driver must be online and not on another trip (availability: " + availability + ")");
        }
        availability = DriverAvailability.ON_TRIP;
    }

    /** Back to AVAILABLE after a trip ends or the driver is released from it. */
    public void endTrip() {
        if (availability == DriverAvailability.ON_TRIP) {
            availability = DriverAvailability.AVAILABLE;
        }
    }

    /** The average is recomputed from all of the driver's ratings by the caller, so rounding never accumulates. */
    public void updateRating(BigDecimal average, int count) {
        this.ratingAvg = average;
        this.ratingCount = count;
    }

    public boolean isOnline() {
        return availability != DriverAvailability.OFFLINE;
    }

    private void requireStatus(Set<DriverVerificationStatus> allowed, String action) {
        if (!allowed.contains(verificationStatus)) {
            throw new InvalidStateException(ErrorCode.INVALID_DRIVER_STATE,
                    "Cannot " + action + " a driver whose verification status is " + verificationStatus);
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public DriverVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public DriverAvailability getAvailability() {
        return availability;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Driver driver && id != null && id.equals(driver.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(Driver.class);
    }
}
