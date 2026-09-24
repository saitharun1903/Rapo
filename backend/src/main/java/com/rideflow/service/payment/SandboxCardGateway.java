package com.rideflow.service.payment;

import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.PaymentProvider;
import com.rideflow.entity.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * Card payments in sandbox mode: <strong>no money moves and no card data exists</strong>. The charge is
 * approved and its reference is derived from the idempotency key, so a retried charge yields the same
 * reference, as it would with a real gateway's idempotency keys. Payments made this way are stored with
 * provider {@code SANDBOX} and the UI labels them as such.
 */
@Component
public class SandboxCardGateway implements PaymentGateway {

    private static final String REFERENCE_PREFIX = "sandbox_";

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        return new ChargeResult(PaymentProvider.SANDBOX, PaymentStatus.CAPTURED,
                REFERENCE_PREFIX + request.idempotencyKey());
    }
}
