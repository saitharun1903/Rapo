package com.rideflow.kafka.outbox;

import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.EventTopic;
import com.rideflow.kafka.event.KafkaNames;
import com.rideflow.monitoring.RequestIdFilter;
import com.rideflow.repository.OutboxMessage;
import com.rideflow.repository.OutboxRepository;
import com.rideflow.service.event.DomainEvent;
import com.rideflow.service.event.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional outbox adapter for {@link DomainEventPublisher}: the event becomes an {@code outbox_events}
 * row in the caller's transaction, and {@link OutboxRelay} sends it to Kafka after the commit. This avoids the
 * dual-write problem of sending to Kafka directly from a transaction (an event for a change that rolled back,
 * or a committed change whose event was lost).
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxRepository outbox;
    private final EventCodec codec;
    private final KafkaNames names;
    private final OutboxRelay relay;
    private final Clock clock;

    public OutboxDomainEventPublisher(OutboxRepository outbox, EventCodec codec, KafkaNames names, OutboxRelay relay,
                                      Clock clock) {
        this.outbox = outbox;
        this.codec = codec;
        this.names = names;
        this.relay = relay;
        this.clock = clock;
    }

    @Override
    public void publish(DomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Domain events must be published inside the transaction that caused them");
        }
        EventTopic topic = EventTopic.of(event);
        if (topic.delivery() != EventTopic.Delivery.OUTBOX) {
            throw new IllegalArgumentException(topic.eventType() + " is not delivered through the outbox");
        }
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        String envelope = codec.encode(eventId, event, now, MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
        outbox.insert(new OutboxMessage(eventId, names.topic(topic), event.aggregateId().toString(),
                topic.eventType(), envelope), now);
        // Wake the relay once the rows are visible, instead of waiting for its next poll.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relay.wakeUp();
            }
        });
    }
}
