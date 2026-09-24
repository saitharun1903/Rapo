package com.rideflow.service.event;

import com.rideflow.repository.ProcessedEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side idempotency. Kafka delivers at least once, so a handler with side effects first records the
 * event here, in the same transaction as those side effects: if the event was already processed, nothing is
 * done, and if the side effects roll back, so does the record and the event is processed again on redelivery.
 */
@Component
public class ProcessedEvents {

    private final ProcessedEventRepository repository;
    private final Clock clock;

    public ProcessedEvents(ProcessedEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @return {@code true} the first time {@code consumer} sees {@code eventId}, {@code false} on a redelivery */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String consumer, UUID eventId) {
        return repository.insertIfAbsent(consumer, eventId, clock.instant());
    }
}
