package com.rideflow.dto.admin;

import com.rideflow.entity.AuditAction;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AuditLogResponse(
        long id,
        @Nullable UUID actorUserId,
        AuditAction action,
        String entityType,
        @Nullable UUID entityId,
        @Nullable Map<String, Object> details,
        Instant createdAt) {
}
