package com.rideflow.entity;

/** Why the server, not the driver, took a driver offline. */
public enum OfflineReason {
    /** No location update within the presence timeout (app closed, no signal). */
    LOCATION_TIMEOUT,
    /** The account was suspended by an admin. */
    ACCOUNT_SUSPENDED
}
