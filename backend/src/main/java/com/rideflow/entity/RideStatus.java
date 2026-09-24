package com.rideflow.entity;

import java.util.EnumSet;
import java.util.Set;

public enum RideStatus {
    REQUESTED,
    MATCHING,
    DRIVER_ASSIGNED,
    DRIVER_ARRIVING,
    DRIVER_ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    /** Statuses in which the passenger has an ongoing ride (mirrors {@code ux_rides_passenger_active}). */
    public static final Set<RideStatus> ACTIVE = EnumSet.of(
            REQUESTED, MATCHING, DRIVER_ASSIGNED, DRIVER_ARRIVING, DRIVER_ARRIVED, IN_PROGRESS);

    /** Statuses in which a driver is attached to the ride (mirrors {@code ux_rides_driver_active}). */
    public static final Set<RideStatus> DRIVER_ENGAGED = EnumSet.of(
            DRIVER_ASSIGNED, DRIVER_ARRIVING, DRIVER_ARRIVED, IN_PROGRESS);

    public static final Set<RideStatus> AWAITING_DRIVER = EnumSet.of(REQUESTED, MATCHING);

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }
}
