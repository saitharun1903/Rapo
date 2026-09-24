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
import java.util.Objects;
import java.util.UUID;

/** The payment for one completed ride, split into the platform's fee and the driver's earnings. */
@Entity
@Table(name = "payments")
public class Payment extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false, updatable = false)
    private UUID rideId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 16, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal platformFee;

    @Column(name = "driver_earnings", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal driverEarnings;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 20, updatable = false)
    private PaymentProvider provider;

    @Column(name = "gateway_reference", length = 80)
    private String gatewayReference;

    protected Payment() {
    }

    /** Amounts already rounded to the currency's minor unit; the fee and the earnings add up to the amount. */
    public record Split(BigDecimal amount, BigDecimal platformFee, BigDecimal driverEarnings, String currency) {

        public Split {
            if (platformFee.add(driverEarnings).compareTo(amount) != 0) {
                throw new IllegalArgumentException("Platform fee and driver earnings must add up to the amount");
            }
        }
    }

    public static Payment of(UUID rideId, PaymentMethod method, Split split, PaymentProvider provider,
                             PaymentStatus status, String gatewayReference) {
        Payment payment = new Payment();
        payment.rideId = rideId;
        payment.method = method;
        payment.amount = split.amount();
        payment.platformFee = split.platformFee();
        payment.driverEarnings = split.driverEarnings();
        payment.currency = split.currency();
        payment.provider = provider;
        payment.status = status;
        payment.gatewayReference = gatewayReference;
        return payment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getPlatformFee() {
        return platformFee;
    }

    public BigDecimal getDriverEarnings() {
        return driverEarnings;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Payment payment && id != null && id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
