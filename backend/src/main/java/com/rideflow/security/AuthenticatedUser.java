package com.rideflow.security;

import com.rideflow.entity.Role;
import java.util.UUID;

/** The principal of an authenticated request, resolved from the access token. */
public record AuthenticatedUser(UUID id, Role role) {
}
