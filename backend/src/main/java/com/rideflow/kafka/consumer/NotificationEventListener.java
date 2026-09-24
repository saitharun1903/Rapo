package com.rideflow.kafka.consumer;

import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.EventDecodingException;
import com.rideflow.kafka.event.ReceivedEvent;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.notification.NotificationService;
import com.rideflow.service.notification.event.NotificationRequestedEvent;
import com.rideflow.service.payment.event.PaymentCreatedEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Creates in-app notifications from ride progress, payments and explicit requests. */
@Component
public class NotificationEventListener {

    private final EventCodec codec;
    private final NotificationService notifications;

    public NotificationEventListener(EventCodec codec, NotificationService notifications) {
        this.codec = codec;
        this.notifications = notifications;
    }

    @KafkaListener(id = "notifications", idIsGroup = false, groupId = "#{@kafkaNames.group('notifications')}",
            topics = "#{@kafkaNames.topics('RIDE_MATCHING', 'RIDE_ACCEPTED', 'RIDE_DRIVER_ARRIVING', 'RIDE_DRIVER_ARRIVED',"
                    + " 'RIDE_STARTED', 'RIDE_COMPLETED', 'RIDE_CANCELLED', 'RIDE_EXPIRED', 'PAYMENT_CREATED',"
                    + " 'NOTIFICATION_REQUESTED')}")
    public void onEvent(ConsumerRecord<String, String> record) {
        ReceivedEvent<DomainEvent> event = codec.decode(record.topic(), record.value());
        TraceContext.run(event.traceId(), () -> {
            switch (event.payload()) {
                case RideStatusChangedEvent change -> notifications.onRideStatusChanged(event.eventId(), change);
                case PaymentCreatedEvent payment -> notifications.onPaymentCreated(event.eventId(), payment);
                case NotificationRequestedEvent request -> notifications.onRequested(event.eventId(), request);
                default -> throw new EventDecodingException(event.topic().eventType() + " does not create notifications");
            }
        });
    }
}
