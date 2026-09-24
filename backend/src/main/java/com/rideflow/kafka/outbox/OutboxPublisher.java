package com.rideflow.kafka.outbox;

import com.rideflow.config.OutboxProperties;
import com.rideflow.kafka.event.EventHeaders;
import com.rideflow.repository.OutboxMessage;
import com.rideflow.repository.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends one batch of outbox rows. The rows stay locked ({@code FOR UPDATE SKIP LOCKED}) while the batch is in
 * flight and are marked published only once the broker has acknowledged them ({@code acks=all}). A row whose
 * send failed or timed out stays unpublished and is sent again later, so delivery is at least once and
 * consumers are idempotent.
 *
 * <p>Ordering: sends are issued in outbox order through one idempotent producer, which keeps them in order per
 * partition. If one send fails and a later one for the same key succeeds, the failed event arrives late; the
 * ride-status consumers tolerate that through {@code aggregateVersion} and state checks.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Clock clock;
    private final Counter published;
    private final Counter failures;

    /** How many rows a run fetched and how many of them the broker acknowledged. */
    public record BatchResult(int fetched, int published) {
    }

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, OutboxProperties properties,
                           Clock clock, MeterRegistry meters) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
        this.published = Counter.builder("rideflow.outbox.published")
                .description("Outbox events acknowledged by Kafka")
                .register(meters);
        this.failures = Counter.builder("rideflow.outbox.failures")
                .description("Outbox sends that failed or timed out (retried on a later run)")
                .register(meters);
        Gauge.builder("rideflow.outbox.pending", outbox, OutboxRepository::countUnpublished)
                .description("Outbox events not yet acknowledged by Kafka")
                .register(meters);
    }

    @Transactional
    public BatchResult publishBatch() {
        List<OutboxMessage> batch = outbox.lockUnpublished(properties.batchSize());
        if (batch.isEmpty()) {
            return new BatchResult(0, 0);
        }
        List<CompletableFuture<SendResult<String, String>>> sends = batch.stream().map(this::send).toList();
        long deadline = System.nanoTime() + properties.sendTimeout().toNanos();
        List<UUID> acknowledged = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            OutboxMessage message = batch.get(i);
            try {
                sends.get(i).get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                acknowledged.add(message.id());
            } catch (ExecutionException ex) {
                recordFailure(message, ex.getCause());
            } catch (TimeoutException ex) {
                recordFailure(message, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                // Rolls back: nothing is marked, and whatever was already sent is sent again (at least once).
                throw new IllegalStateException("Interrupted while waiting for Kafka acknowledgements", ex);
            }
        }
        outbox.markPublished(acknowledged, clock.instant());
        published.increment(acknowledged.size());
        return new BatchResult(batch.size(), acknowledged.size());
    }

    private CompletableFuture<SendResult<String, String>> send(OutboxMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(message.topic(), null, message.key(),
                message.payload(), EventHeaders.of(message.eventType()));
        try {
            return kafka.send(record);
        } catch (RuntimeException ex) {
            // The producer can also fail synchronously (no metadata within max.block.ms, buffer full); handled
            // like an asynchronous failure: recorded on the row and retried on a later run.
            return CompletableFuture.failedFuture(ex);
        }
    }

    private void recordFailure(OutboxMessage message, Throwable cause) {
        failures.increment();
        String error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        outbox.recordFailure(message.id(), error);
        log.warn("Outbox event {} ({}) not acknowledged by Kafka, will retry: {}", message.id(), message.eventType(),
                error);
    }
}
