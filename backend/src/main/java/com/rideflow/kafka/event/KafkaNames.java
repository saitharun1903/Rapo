package com.rideflow.kafka.event;

import com.rideflow.config.EventingProperties;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deployed topic and consumer group names: the configured prefix plus the base name. Listeners reference this
 * bean from SpEL ({@code #{@kafkaNames.topics('RIDE_COMPLETED')}}), so names are computed in one place.
 */
@Component("kafkaNames")
public class KafkaNames {

    /** Dead-letter topics are the original name plus this suffix. */
    public static final String DLT_SUFFIX = ".DLT";

    private static final String REALTIME_GROUP = "realtime-";

    private final String prefix;
    private final String instanceId = UUID.randomUUID().toString();
    private final Map<EventTopic, String> names = new EnumMap<>(EventTopic.class);
    private final Map<String, EventTopic> byName = new HashMap<>();

    public KafkaNames(EventingProperties properties) {
        this.prefix = properties.prefix();
        for (EventTopic topic : EventTopic.values()) {
            String name = prefix + topic.baseName();
            names.put(topic, name);
            byName.put(name, topic);
        }
    }

    public String topic(EventTopic topic) {
        return names.get(topic);
    }

    /** For listener annotations: deployed names of the given {@link EventTopic} constants. */
    public String[] topics(String... topicConstants) {
        return Arrays.stream(topicConstants).map(EventTopic::valueOf).map(this::topic).toArray(String[]::new);
    }

    public String group(String baseName) {
        return prefix + baseName;
    }

    /**
     * A consumer group of its own for this instance, so every instance receives every event and can push it to
     * the WebSocket clients connected to it.
     */
    public String realtimeGroup() {
        return prefix + REALTIME_GROUP + instanceId;
    }

    public Optional<EventTopic> fromName(String deployedName) {
        return Optional.ofNullable(byName.get(deployedName));
    }

    public static String deadLetterTopic(String topic) {
        return topic + DLT_SUFFIX;
    }
}
