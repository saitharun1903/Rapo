package com.rideflow.support;

import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestUsers {

    private TestUsers() {
    }

    /** A detached user with an id, as if loaded from the database. */
    public static User withRole(Role role) {
        UUID id = UUID.randomUUID();
        User user = User.register(role.name().toLowerCase() + "-" + id + "@example.com", null, "{bcrypt}unused",
                "Test " + role, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
