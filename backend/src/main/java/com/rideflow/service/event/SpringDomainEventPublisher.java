package com.rideflow.service.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** In-process adapter: consumers use {@code @TransactionalEventListener(phase = AFTER_COMMIT)}. */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}
