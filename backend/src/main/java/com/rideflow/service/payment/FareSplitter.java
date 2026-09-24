package com.rideflow.service.payment;

import com.rideflow.config.PaymentProperties;
import com.rideflow.entity.Payment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.springframework.stereotype.Component;

/**
 * Splits a fare into the platform's fee and the driver's earnings. The fee is rounded to the currency's minor
 * unit and the driver gets the exact remainder, so the two always add up to the fare.
 */
@Component
public class FareSplitter {

    private final BigDecimal platformFeeRate;

    public FareSplitter(PaymentProperties properties) {
        this.platformFeeRate = properties.platformFeeRate();
    }

    public Payment.Split split(BigDecimal fare, String currency) {
        int scale = Currency.getInstance(currency).getDefaultFractionDigits();
        BigDecimal amount = fare.setScale(scale, RoundingMode.UNNECESSARY);
        BigDecimal fee = amount.multiply(platformFeeRate).setScale(scale, RoundingMode.HALF_UP);
        return new Payment.Split(amount, fee, amount.subtract(fee), currency);
    }
}
