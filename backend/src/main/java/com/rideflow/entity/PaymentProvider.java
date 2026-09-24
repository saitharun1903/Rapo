package com.rideflow.entity;

/** Who handled the money: the driver, in cash, or the sandbox card gateway (no real money moves). */
public enum PaymentProvider {
    CASH,
    SANDBOX
}
