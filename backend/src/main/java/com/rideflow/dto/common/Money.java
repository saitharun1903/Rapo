package com.rideflow.dto.common;

import java.math.BigDecimal;

/** Monetary amount serialised as a decimal string, so JavaScript clients never round it through a float. */
public record Money(String amount, String currency) {

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount.toPlainString(), currency);
    }
}
