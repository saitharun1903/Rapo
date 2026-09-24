package com.rideflow.service.payment.event;

import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.PaymentStatus;
import com.rideflow.service.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A completed ride's payment was recorded. */
public record PaymentCreatedEvent(
        UUID paymentId,
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal driverEarnings,
        Instant occurredAt) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return paymentId;
    }
}
