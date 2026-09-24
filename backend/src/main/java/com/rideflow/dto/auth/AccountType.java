package com.rideflow.dto.auth;

import com.rideflow.entity.Role;

/** Self-service account types. ADMIN is intentionally absent: admins cannot self-register. */
public enum AccountType {
    PASSENGER(Role.PASSENGER),
    DRIVER(Role.DRIVER);

    private final Role role;

    AccountType(Role role) {
        this.role = role;
    }

    public Role role() {
        return role;
    }
}
