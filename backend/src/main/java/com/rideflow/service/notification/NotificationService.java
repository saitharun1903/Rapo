package com.rideflow.service.notification;

import com.rideflow.dto.common.PageResponse;
import com.rideflow.dto.notification.NotificationResponse;
import com.rideflow.entity.Notification;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.ResourceNotFoundException;
import com.rideflow.repository.NotificationRepository;
import com.rideflow.service.event.DomainEventPublisher;
import com.rideflow.service.event.ProcessedEvents;
import com.rideflow.service.notification.NotificationComposer.Draft;
import com.rideflow.service.notification.event.NotificationCreatedEvent;
import com.rideflow.service.notification.event.NotificationRequestedEvent;
import com.rideflow.service.payment.event.PaymentCreatedEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications. Created by the notifications consumer from ride, payment and request events; each
 * one is stored and then announced on {@code notification.created}, which every instance's realtime bridge
 * pushes to the user if they are connected there.
 */
@Service
public class NotificationService {

    /** Idempotency key of the notifications consumer ({@code processed_events.consumer}). */
    private static final String CONSUMER = "notifications";

    private final NotificationRepository notifications;
    private final NotificationComposer composer;
    private final ProcessedEvents processedEvents;
    private final DomainEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, NotificationComposer composer,
                               ProcessedEvents processedEvents, DomainEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.composer = composer;
        this.processedEvents = processedEvents;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void onRideStatusChanged(UUID eventId, RideStatusChangedEvent event) {
        createOnce(eventId, composer.forRideStatus(event));
    }

    @Transactional
    public void onPaymentCreated(UUID eventId, PaymentCreatedEvent event) {
        createOnce(eventId, composer.forPayment(event));
    }

    @Transactional
    public void onRequested(UUID eventId, NotificationRequestedEvent event) {
        createOnce(eventId, List.of(composer.forRequest(event)));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNull(userId, pageable)
                : notifications.findByUserId(userId, pageable);
        return PageResponse.of(page, NotificationService::toResponse);
    }

    /** Another user's notification is reported as not found, so ids cannot be probed. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND, "Notification not found"))
                .markRead(clock.instant());
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, clock.instant());
    }

    private void createOnce(UUID eventId, List<Draft> drafts) {
        if (drafts.isEmpty() || !processedEvents.firstDelivery(CONSUMER, eventId)) {
            return;
        }
        for (Draft draft : drafts) {
            Notification notification = notifications.saveAndFlush(Notification.of(draft.userId(), draft.type(),
                    draft.title(), draft.body(), draft.rideId(), eventId));
            events.publish(new NotificationCreatedEvent(notification.getId(), notification.getUserId(),
                    notification.getType(), notification.getTitle(), notification.getBody(), notification.getRideId(),
                    notification.getCreatedAt()));
        }
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getBody(), notification.getRideId(), notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
