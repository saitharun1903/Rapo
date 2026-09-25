package com.rideflow.dto.notification;

import com.rideflow.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A notification, as listed by the API and pushed to {@code /user/queue/notifications}. */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        @Nullable UUID rideId,
        boolean read,
        Instant createdAt) {
}
