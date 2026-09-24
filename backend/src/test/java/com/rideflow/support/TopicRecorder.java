package com.rideflow.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Reads what actually arrived on Kafka topics, independently of the application's consumers: it is assigned
 * all partitions of the given topics from the beginning, outside any consumer group. Use from the test thread
 * only (Kafka consumers are not thread-safe).
 */
public final class TopicRecorder implements AutoCloseable {

    private static final Duration POLL = Duration.ofMillis(100);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Consumer<Object, Object> consumer;
    private final List<ConsumerRecord<Object, Object>> received = new ArrayList<>();

    public TopicRecorder(ConsumerFactory<Object, Object> factory, Collection<String> topics) {
        this.consumer = factory.createConsumer("test-recorder-" + UUID.randomUUID(), null);
        List<TopicPartition> partitions = topics.stream()
                .flatMap(topic -> consumer.partitionsFor(topic).stream()
                        .map(info -> new TopicPartition(topic, info.partition())))
                .toList();
        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);
    }

    /** Polls until at least one record matches, then returns every matching record received so far. */
    public List<ConsumerRecord<Object, Object>> awaitRecords(Predicate<ConsumerRecord<Object, Object>> matching) {
        return awaitRecords(matching, records -> !records.isEmpty());
    }

    /** Polls until the matching records received so far satisfy {@code complete}, and returns them. */
    public List<ConsumerRecord<Object, Object>> awaitRecords(Predicate<ConsumerRecord<Object, Object>> matching,
                                                             Predicate<List<ConsumerRecord<Object, Object>>> complete) {
        // Same thread: a Kafka consumer must not be used from Awaitility's polling thread.
        await().atMost(TIMEOUT).pollInSameThread().until(() -> {
            consumer.poll(POLL).forEach(received::add);
            return complete.test(received.stream().filter(matching).toList());
        });
        return received.stream().filter(matching).toList();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
