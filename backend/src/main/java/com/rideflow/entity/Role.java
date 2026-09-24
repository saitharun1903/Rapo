package com.rideflow.entity;

public enum Role {
    PASSENGER,
    DRIVER,
    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    /** Spring Security authority name, matching {@code hasRole(...)} checks. */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
