package com.rideflow.service.driver;

import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;

/**
 * Port for the stream of accepted driver positions. Unlike {@code DomainEventPublisher} it is not
 * transactional and delivers at most once: a lost position is superseded by the next report a few seconds
 * later, and writing every report through the outbox would put the load back on PostgreSQL.
 */
public interface LocationStream {

    void publish(DriverLocationUpdatedEvent event);
}
