package com.rideflow.kafka.consumer;

import com.rideflow.config.KafkaConfig;
import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.EventDecodingException;
import com.rideflow.kafka.event.ReceivedEvent;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import com.rideflow.service.driver.event.DriverWentOfflineEvent;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.notification.event.NotificationCreatedEvent;
import com.rideflow.service.ride.event.RideOffersCreatedEvent;
import com.rideflow.service.ride.event.RideOffersWithdrawnEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import com.rideflow.websocket.RealtimePublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds WebSocket pushes from Kafka. Its consumer group is unique to this instance, so every instance sees
 * every event and delivers it to the clients connected to it; a client may be connected to any instance.
 * It starts from the latest offset (a restarted instance pushes what happens next, not history) and does not
 * retry: pushes are best effort, and clients re-sync from REST snapshots on reconnect.
 */
@Component
public class RealtimeBridgeListener {

    private final EventCodec codec;
    private final RealtimePublisher realtime;

    public RealtimeBridgeListener(EventCodec codec, RealtimePublisher realtime) {
        this.codec = codec;
        this.realtime = realtime;
    }

    @KafkaListener(id = "realtime-bridge", idIsGroup = false, containerFactory = KafkaConfig.REALTIME_CONTAINER_FACTORY,
            groupId = "#{@kafkaNames.realtimeGroup()}", properties = "auto.offset.reset=latest",
            topics = "#{@kafkaNames.topics('RIDE_REQUESTED', 'RIDE_MATCHING', 'RIDE_ACCEPTED', 'RIDE_DRIVER_ARRIVING',"
                    + " 'RIDE_DRIVER_ARRIVED', 'RIDE_STARTED', 'RIDE_COMPLETED', 'RIDE_CANCELLED', 'RIDE_EXPIRED',"
                    + " 'RIDE_DRIVER_ASSIGNED', 'RIDE_OFFERS_WITHDRAWN', 'DRIVER_LOCATION_UPDATED', 'DRIVER_WENT_OFFLINE',"
                    + " 'NOTIFICATION_CREATED')}")
    public void onEvent(ConsumerRecord<String, String> record) {
        ReceivedEvent<DomainEvent> event = codec.decode(record.topic(), record.value());
        TraceContext.run(event.traceId(), () -> {
            switch (event.payload()) {
                case RideStatusChangedEvent change -> realtime.rideStatusChanged(change);
                case RideOffersCreatedEvent offers -> realtime.offersCreated(offers);
                case RideOffersWithdrawnEvent withdrawn -> realtime.offersWithdrawn(withdrawn);
                case DriverLocationUpdatedEvent location -> realtime.driverLocationUpdated(location);
                case DriverWentOfflineEvent offline -> realtime.driverWentOffline(offline);
                case NotificationCreatedEvent notification -> realtime.notificationCreated(notification);
                default -> throw new EventDecodingException(event.topic().eventType() + " has no WebSocket push");
            }
        });
    }
}
