package com.rideflow.service.ride.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** In-process adapter: consumers use {@code @TransactionalEventListener(phase = AFTER_COMMIT)}. */
@Component
public class SpringRideEventPublisher implements RideEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringRideEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}
