package com.rideflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Snapshot of a fare calculation (the quoted estimate or the final metered fare), kept so historical
 * fares stay explainable even after pricing configuration changes.
 */
@Entity
@Table(name = "fare_breakdowns")
public class FareBreakdown {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false, updatable = false)
    private UUID rideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10, updatable = false)
    private FareKind kind;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "distance_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceCharge;

    @Column(name = "time_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal timeCharge;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "surge_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal surgeMultiplier;

    @Column(name = "booking_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal bookingFee;

    @Column(name = "minimum_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumFare;

    @Column(name = "minimum_fare_applied", nullable = false)
    private boolean minimumFareApplied;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "distance_m", nullable = false)
    private int distanceMeters;

    @Column(name = "duration_s", nullable = false)
    private int durationSeconds;

    @Column(name = "pricing_version", nullable = false, length = 20)
    private String pricingVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FareBreakdown() {
    }

    /** Field values of a single fare calculation. */
    public record Amounts(
            BigDecimal baseFare,
            BigDecimal distanceCharge,
            BigDecimal timeCharge,
            BigDecimal subtotal,
            BigDecimal surgeMultiplier,
            BigDecimal bookingFee,
            BigDecimal minimumFare,
            boolean minimumFareApplied,
            BigDecimal total,
            String currency,
            int distanceMeters,
            int durationSeconds,
            String pricingVersion) {
    }

    public static FareBreakdown of(UUID rideId, FareKind kind, Amounts amounts) {
        FareBreakdown breakdown = new FareBreakdown();
        breakdown.rideId = rideId;
        breakdown.kind = kind;
        breakdown.baseFare = amounts.baseFare();
        breakdown.distanceCharge = amounts.distanceCharge();
        breakdown.timeCharge = amounts.timeCharge();
        breakdown.subtotal = amounts.subtotal();
        breakdown.surgeMultiplier = amounts.surgeMultiplier();
        breakdown.bookingFee = amounts.bookingFee();
        breakdown.minimumFare = amounts.minimumFare();
        breakdown.minimumFareApplied = amounts.minimumFareApplied();
        breakdown.total = amounts.total();
        breakdown.currency = amounts.currency();
        breakdown.distanceMeters = amounts.distanceMeters();
        breakdown.durationSeconds = amounts.durationSeconds();
        breakdown.pricingVersion = amounts.pricingVersion();
        return breakdown;
    }

    public Amounts amounts() {
        return new Amounts(baseFare, distanceCharge, timeCharge, subtotal, surgeMultiplier, bookingFee, minimumFare,
                minimumFareApplied, total, currency, distanceMeters, durationSeconds, pricingVersion);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public FareKind getKind() {
        return kind;
    }

    public BigDecimal getTotal() {
        return total;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof FareBreakdown breakdown && id != null && id.equals(breakdown.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(FareBreakdown.class);
    }
}
