package com.rideflow.kafka.consumer;

import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.ReceivedEvent;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.payment.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Settles every completed ride ({@code ride.completed}). */
@Component
public class PaymentEventListener {

    private final EventCodec codec;
    private final PaymentService payments;

    public PaymentEventListener(EventCodec codec, PaymentService payments) {
        this.codec = codec;
        this.payments = payments;
    }

    @KafkaListener(id = "payments", idIsGroup = false, groupId = "#{@kafkaNames.group('payments')}",
            topics = "#{@kafkaNames.topics('RIDE_COMPLETED')}")
    public void onRideCompleted(ConsumerRecord<String, String> record) {
        ReceivedEvent<DomainEvent> event = codec.decode(record.topic(), record.value());
        TraceContext.run(event.traceId(),
                () -> payments.settleCompletedRide(event.eventId(), event.payload().aggregateId()));
    }
}
