package com.rideflow.repository;

import com.rideflow.entity.Role;
import com.rideflow.entity.User;
import com.rideflow.entity.UserStatus;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Composable, parameter-bound filters for the admin user search (no string-built SQL). */
public final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Case-insensitive substring match on name or email. */
    public static Specification<User> matchesText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
