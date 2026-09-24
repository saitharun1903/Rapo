package com.rideflow.service.notification.event;

import com.rideflow.entity.NotificationType;
import com.rideflow.service.event.DomainEvent;
import java.util.UUID;

/**
 * Another module asks for a user to be notified, without depending on how notifications are stored or sent.
 *
 * @param detail optional text the notification includes (for example a rejection reason); never personal data
 */
public record NotificationRequestedEvent(UUID userId, NotificationType type, UUID rideId, String detail)
        implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return userId;
    }
}
