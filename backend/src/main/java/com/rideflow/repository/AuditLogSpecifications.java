package com.rideflow.repository;

import com.rideflow.entity.AuditAction;
import com.rideflow.entity.AuditLog;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/** Composable, parameter-bound filters for the admin audit-log search. A {@code null} argument means "any". */
public final class AuditLogSpecifications {

    private static final String CREATED_AT = "createdAt";

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> hasAction(AuditAction action) {
        return (root, query, cb) -> action == null ? null : cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> hasEntityType(String entityType) {
        return (root, query, cb) -> entityType == null || entityType.isBlank() ? null
                : cb.equal(root.get("entityType"), entityType.trim());
    }

    public static Specification<AuditLog> createdFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get(CREATED_AT), from);
    }

    public static Specification<AuditLog> createdBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get(CREATED_AT), to);
    }
}
