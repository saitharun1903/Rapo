package com.rideflow.config;

import com.rideflow.kafka.event.EventDecodingException;
import com.rideflow.kafka.event.EventTopic;
import com.rideflow.kafka.event.KafkaNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Topics, retries and dead-letter handling (docs/events.md section 1.4).
 *
 * <ul>
 *   <li>Every topic and its {@code .DLT} is declared here; the broker does not create topics implicitly.</li>
 *   <li>Shared consumer groups (matching, payments, notifications, location persistence) retry a failed record
 *       with exponential backoff and then publish it to {@code <topic>.DLT}. Records that can never succeed
 *       ({@link EventDecodingException}) go to the DLT at once.</li>
 *   <li>The realtime bridge has a group per instance and only pushes to WebSocket clients: it neither retries
 *       nor dead-letters (every instance would write the same record to the DLT), it logs and moves on.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EventingProperties.class, OutboxProperties.class, PaymentProperties.class})
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /** Container factory of the realtime bridge. */
    public static final String REALTIME_CONTAINER_FACTORY = "realtimeContainerFactory";

    @Bean
    KafkaAdmin.NewTopics rideflowTopics(EventingProperties properties, KafkaNames names) {
        List<NewTopic> topics = new ArrayList<>();
        for (EventTopic topic : EventTopic.values()) {
            String name = names.topic(topic);
            topics.add(topic(name, retention(properties.retention(), topic.retention()), properties));
            topics.add(topic(KafkaNames.deadLetterTopic(name), properties.retention().deadLetter(), properties));
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    /** Picked up by Spring Boot for the default listener container factory. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, EventingProperties properties,
                                         MeterRegistry meters) {
        // Same partition in the DLT, so a replay keeps per-key order; both topics have the same partition count.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> {
            Counter.builder("rideflow.kafka.dead.letters")
                    .description("Records moved to a dead-letter topic after their handler failed")
                    .tag("topic", record.topic())
                    .register(meters)
                    .increment();
            log.error("Moving {}-{}@{} to the dead-letter topic: {}", record.topic(), record.partition(),
                    record.offset(), ex.getMessage());
            return new TopicPartition(KafkaNames.deadLetterTopic(record.topic()), record.partition());
        });
        EventingProperties.Retry retry = properties.retry();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.attempts());
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(EventDecodingException.class);
        return handler;
    }

    /** Same consumer settings as the default factory, without retries or dead-lettering. */
    @Bean(REALTIME_CONTAINER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<Object, Object> realtimeContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(new CommonLoggingErrorHandler());
        return factory;
    }

    private static NewTopic topic(String name, Duration retention, EventingProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retention.toMillis()))
                .build();
    }

    private static Duration retention(EventingProperties.Retention retention, EventTopic.Retention kind) {
        return switch (kind) {
            case LIFECYCLE -> retention.lifecycle();
            case LOCATION -> retention.location();
            case NOTIFICATION -> retention.notification();
        };
    }
}
