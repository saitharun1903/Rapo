package com.rideflow.service.payment;

import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.PaymentProvider;
import com.rideflow.entity.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Collects a completed ride's fare. One implementation per {@link PaymentMethod}. A real card gateway would
 * receive {@link ChargeRequest#idempotencyKey()} so that a charge retried after a failure (the payment
 * consumer is retried by Kafka) is not taken twice.
 */
public interface PaymentGateway {

    PaymentMethod method();

    ChargeResult charge(ChargeRequest request);

    /** @param idempotencyKey the ride id: at most one charge per ride */
    record ChargeRequest(UUID idempotencyKey, BigDecimal amount, String currency) {
    }

    /** @param reference the provider's transaction reference, if it issues one */
    record ChargeResult(PaymentProvider provider, PaymentStatus status, String reference) {
    }
}
