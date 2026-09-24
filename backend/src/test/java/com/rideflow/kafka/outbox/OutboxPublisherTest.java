package com.rideflow.kafka.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rideflow.config.OutboxProperties;
import com.rideflow.kafka.outbox.OutboxPublisher.BatchResult;
import com.rideflow.repository.OutboxMessage;
import com.rideflow.repository.OutboxRepository;
import com.rideflow.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final OutboxPublisher publisher = new OutboxPublisher(outbox, kafka,
            new OutboxProperties(true, 100, Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofSeconds(5),
                    Duration.ofDays(3)),
            new MutableClock(NOW), meters);

    private static ProducerRecord<String, String> anyRecord() {
        return any();
    }

    private static OutboxMessage message(String key) {
        return new OutboxMessage(UUID.randomUUID(), "ride.completed", key, "ride.completed", "{}");
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        return CompletableFuture.completedFuture(null);
    }

    @Test
    void onlyAcknowledgedEventsAreMarkedPublishedAndFailuresAreRecordedForRetry() {
        OutboxMessage first = message("a");
        OutboxMessage failing = message("b");
        OutboxMessage third = message("c");
        when(outbox.lockUnpublished(100)).thenReturn(List.of(first, failing, third));
        when(kafka.send(argThat((ProducerRecord<String, String> record) -> record != null && !"b".equals(record.key()))))
                .thenReturn(acknowledged());
        when(kafka.send(argThat((ProducerRecord<String, String> record) -> record != null && "b".equals(record.key()))))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")));

        BatchResult result = publisher.publishBatch();

        assertThat(result).isEqualTo(new BatchResult(3, 2));
        verify(outbox).markPublished(argThat((Collection<UUID> ids) ->
                ids.size() == 2 && ids.contains(first.id()) && ids.contains(third.id())), eq(NOW));
        verify(outbox).recordFailure(eq(failing.id()), argThat(error -> error.contains("broker unreachable")));
        assertThat(meters.get("rideflow.outbox.failures").counter().count()).isEqualTo(1);
        assertThat(meters.get("rideflow.outbox.published").counter().count()).isEqualTo(2);
    }

    @Test
    void recordsCarryTheKeyAndEventHeaders() {
        OutboxMessage only = message("ride-1");
        when(outbox.lockUnpublished(anyInt())).thenReturn(List.of(only));
        when(kafka.send(anyRecord())).thenReturn(acknowledged());

        publisher.publishBatch();

        verify(kafka).send(argThat((ProducerRecord<String, String> record) -> record.key().equals("ride-1")
                && record.topic().equals("ride.completed")
                && record.headers().lastHeader("eventType") != null
                && record.headers().lastHeader("schemaVersion") != null));
    }

    @Test
    void aSynchronousProducerFailureIsHandledLikeAnAsynchronousOne() {
        OutboxMessage only = message("a");
        when(outbox.lockUnpublished(anyInt())).thenReturn(List.of(only));
        when(kafka.send(anyRecord())).thenThrow(new TimeoutException("no metadata"));

        assertThat(publisher.publishBatch()).isEqualTo(new BatchResult(1, 0));
        verify(outbox).recordFailure(eq(only.id()), argThat(error -> error.contains("no metadata")));
    }

    @Test
    void anEmptyOutboxSendsNothing() {
        when(outbox.lockUnpublished(anyInt())).thenReturn(List.of());

        assertThat(publisher.publishBatch()).isEqualTo(new BatchResult(0, 0));
        verify(kafka, never()).send(anyRecord());
    }
}
