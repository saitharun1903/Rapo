package com.rideflow.kafka.consumer;

import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.EventDecodingException;
import com.rideflow.kafka.event.ReceivedEvent;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.matching.DriverMatchingService;
import com.rideflow.service.ride.event.MatchingRoundRequestedEvent;
import com.rideflow.service.ride.event.RideStatusChangedEvent;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Starts matching for new rides ({@code ride.requested}) and for rides that need another round early. */
@Component
public class MatchingEventListener {

    private final EventCodec codec;
    private final DriverMatchingService matching;

    public MatchingEventListener(EventCodec codec, DriverMatchingService matching) {
        this.codec = codec;
        this.matching = matching;
    }

    @KafkaListener(id = "matching", idIsGroup = false, groupId = "#{@kafkaNames.group('matching')}",
            topics = "#{@kafkaNames.topics('RIDE_REQUESTED', 'RIDE_DISPATCH_REQUESTED')}")
    public void onEvent(ConsumerRecord<String, String> record) {
        ReceivedEvent<DomainEvent> event = codec.decode(record.topic(), record.value());
        UUID rideId = switch (event.payload()) {
            case RideStatusChangedEvent change -> change.rideId();
            case MatchingRoundRequestedEvent request -> request.rideId();
            default -> throw new EventDecodingException(event.topic().eventType() + " is not a matching trigger");
        };
        TraceContext.run(event.traceId(), () -> matching.runNextRoundOnce(event.eventId(), rideId));
    }
}
