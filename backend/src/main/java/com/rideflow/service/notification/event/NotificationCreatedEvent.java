package com.rideflow.service.notification.event;

import com.rideflow.entity.NotificationType;
import com.rideflow.service.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A notification was stored. Every instance consumes this and pushes it to the user if they are connected
 * there. Keyed by user, so one user's notifications arrive in order.
 */
public record NotificationCreatedEvent(
        UUID notificationId,
        UUID userId,
        NotificationType type,
        String title,
        String body,
        UUID rideId,
        Instant createdAt) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return userId;
    }
}
