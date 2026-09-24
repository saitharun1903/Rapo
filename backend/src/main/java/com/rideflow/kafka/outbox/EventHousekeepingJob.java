package com.rideflow.kafka.outbox;

import com.rideflow.config.EventingProperties;
import com.rideflow.config.OutboxProperties;
import com.rideflow.repository.OutboxRepository;
import com.rideflow.repository.ProcessedEventRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the event tables small: published outbox rows are only needed for investigation for a few days, and
 * idempotency records only for longer than any redelivery can take. Each purge is one indexed DELETE, safe to
 * run on every instance.
 */
@Component
public class EventHousekeepingJob {

    private static final Logger log = LoggerFactory.getLogger(EventHousekeepingJob.class);

    private final OutboxRepository outbox;
    private final ProcessedEventRepository processedEvents;
    private final OutboxProperties outboxProperties;
    private final EventingProperties eventingProperties;
    private final Clock clock;

    public EventHousekeepingJob(OutboxRepository outbox, ProcessedEventRepository processedEvents,
                                OutboxProperties outboxProperties, EventingProperties eventingProperties, Clock clock) {
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.outboxProperties = outboxProperties;
        this.eventingProperties = eventingProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${rideflow.kafka.cleanup-cron}")
    public void purge() {
        Instant now = clock.instant();
        int outboxRows = outbox.deletePublishedBefore(now.minus(outboxProperties.retention()));
        int processedRows = processedEvents.deleteProcessedBefore(now.minus(eventingProperties.processedEventsRetention()));
        log.info("Purged {} published outbox events and {} idempotency records", outboxRows, processedRows);
    }
}
