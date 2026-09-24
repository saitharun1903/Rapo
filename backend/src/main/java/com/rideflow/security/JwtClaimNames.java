package com.rideflow.security;

/** Custom claim names used in RideFlow access tokens (standard claims come from Spring's JwtClaimNames). */
public final class JwtClaimNames {

    public static final String ROLE = "role";

    private JwtClaimNames() {
    }
}
