package com.rideflow.support;

import static org.awaitility.Awaitility.await;

import com.rideflow.kafka.event.KafkaNames;
import com.rideflow.repository.OutboxRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Lets integration tests wait for the asynchronous event pipeline instead of sleeping:
 * <ul>
 *   <li>{@link #awaitAssignment()}: every listener of this context has its partitions. The realtime bridge
 *       starts at the latest offset, so events sent before that would never be pushed.</li>
 *   <li>{@link #awaitIdle()}: the outbox is empty and every shared consumer group has processed everything
 *       on its topics, including events that processing itself produced.</li>
 * </ul>
 */
public class KafkaTestSupport implements DisposableBean {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL = Duration.ofMillis(50);
    /** Consumer groups whose work tests wait for; the realtime bridge only pushes and is not waited on. */
    private static final List<String> SHARED_GROUPS =
            List.of("matching", "payments", "notifications", "location-persistence");

    private final KafkaListenerEndpointRegistry registry;
    private final KafkaNames names;
    private final OutboxRepository outbox;
    private final Admin admin;

    public KafkaTestSupport(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin, KafkaNames names,
                            OutboxRepository outbox) {
        this.registry = registry;
        this.names = names;
        this.outbox = outbox;
        this.admin = Admin.create(kafkaAdmin.getConfigurationProperties());
    }

    public void awaitAssignment() {
        await().atMost(TIMEOUT).pollInterval(POLL).until(() -> registry.getListenerContainers().stream()
                .map(MessageListenerContainer::getAssignedPartitions)
                .allMatch(partitions -> partitions != null && !partitions.isEmpty()));
    }

    public void awaitIdle() {
        // Groups, outbox, groups again: an event produced while the first check ran shows up in the second.
        await().atMost(TIMEOUT).pollInterval(POLL)
                .until(() -> groupsCaughtUp() && outbox.countUnpublished() == 0 && groupsCaughtUp());
    }

    private boolean groupsCaughtUp() throws ExecutionException, InterruptedException {
        List<String> groups = SHARED_GROUPS.stream().map(names::group).toList();
        Map<String, ConsumerGroupDescription> descriptions = admin.describeConsumerGroups(groups).all().get();
        for (String group : groups) {
            Set<TopicPartition> assigned = descriptions.get(group).members().stream()
                    .map(MemberDescription::assignment)
                    .flatMap(assignment -> assignment.topicPartitions().stream())
                    .collect(Collectors.toSet());
            if (assigned.isEmpty()) {
                return false;
            }
            Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
            assigned.forEach(partition -> latest.put(partition, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResultInfo> ends = admin.listOffsets(latest).all().get();
            Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get();
            for (TopicPartition partition : assigned) {
                OffsetAndMetadata position = committed.get(partition);
                long consumed = position == null ? 0 : position.offset();
                if (consumed < ends.get(partition).offset()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void destroy() {
        admin.close();
    }
}
