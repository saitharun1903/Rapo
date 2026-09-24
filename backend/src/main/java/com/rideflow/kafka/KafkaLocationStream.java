package com.rideflow.kafka;

import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.EventHeaders;
import com.rideflow.kafka.event.EventTopic;
import com.rideflow.kafka.event.KafkaNames;
import com.rideflow.monitoring.RequestIdFilter;
import com.rideflow.service.driver.LocationStream;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends position reports straight to {@code driver.location.updated}, keyed by driver so each driver's reports
 * stay in order. Fire and forget: the caller never waits for the broker, and a failed send is counted
 * ({@code rideflow.location.publish.failures}), not retried. While sends keep failing, only the first failure
 * and the recovery are logged, so a Kafka outage does not flood the log once per report.
 */
@Component
public class KafkaLocationStream implements LocationStream {

    private static final Logger log = LoggerFactory.getLogger(KafkaLocationStream.class);

    private final KafkaTemplate<String, String> kafka;
    private final EventCodec codec;
    private final String topic;
    private final Clock clock;
    private final Counter failures;
    private final AtomicBoolean failing = new AtomicBoolean();

    public KafkaLocationStream(KafkaTemplate<String, String> kafka, EventCodec codec, KafkaNames names, Clock clock,
                               MeterRegistry meters) {
        this.kafka = kafka;
        this.codec = codec;
        this.topic = names.topic(EventTopic.DRIVER_LOCATION_UPDATED);
        this.clock = clock;
        this.failures = Counter.builder("rideflow.location.publish.failures")
                .description("Driver position reports that could not be sent to Kafka (dropped)")
                .register(meters);
    }

    @Override
    public void publish(DriverLocationUpdatedEvent event) {
        String payload = codec.encode(UUID.randomUUID(), event, clock.instant(), MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
        try {
            kafka.send(new ProducerRecord<>(topic, null, event.driverId().toString(), payload,
                            EventHeaders.of(EventTopic.DRIVER_LOCATION_UPDATED.eventType())))
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            recovered();
                        } else {
                            failed(ex);
                        }
                    });
        } catch (RuntimeException ex) {
            // Synchronous producer failure (no metadata within max.block.ms, buffer full): same as async.
            failed(ex);
        }
    }

    private void failed(Throwable ex) {
        failures.increment();
        if (failing.compareAndSet(false, true)) {
            log.warn("Driver positions are not reaching Kafka (counting further failures silently until it recovers): {}",
                    ex.toString());
        }
    }

    private void recovered() {
        if (failing.compareAndSet(true, false)) {
            log.info("Driver positions are reaching Kafka again");
        }
    }
}
