package com.rideflow.service.payment;

import com.rideflow.entity.PaymentMethod;
import com.rideflow.entity.PaymentProvider;
import com.rideflow.entity.PaymentStatus;
import org.springframework.stereotype.Component;

/** Cash is collected by the driver at the end of the trip, so the payment is recorded as captured. */
@Component
public class CashPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        return new ChargeResult(PaymentProvider.CASH, PaymentStatus.CAPTURED, null);
    }
}
