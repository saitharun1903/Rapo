package com.rideflow.kafka.consumer;

import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.EventDecodingException;
import com.rideflow.service.driver.LocationPersistenceService;
import com.rideflow.service.driver.event.DriverLocationUpdatedEvent;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

/**
 * Persists driver positions in batches: whatever one poll returns (up to {@code max.poll.records}) is written
 * in one transaction. An undecodable record does not block the rest: the records before it are persisted,
 * the error handler sends it to the DLT, and consumption continues after it.
 */
@Component
public class LocationPersistenceListener {

    private final EventCodec codec;
    private final LocationPersistenceService persistence;

    public LocationPersistenceListener(EventCodec codec, LocationPersistenceService persistence) {
        this.codec = codec;
        this.persistence = persistence;
    }

    @KafkaListener(id = "location-persistence", idIsGroup = false, batch = "true",
            groupId = "#{@kafkaNames.group('location-persistence')}",
            topics = "#{@kafkaNames.topics('DRIVER_LOCATION_UPDATED')}")
    public void onBatch(List<ConsumerRecord<String, String>> records) {
        List<DriverLocationUpdatedEvent> reports = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, String> record = records.get(i);
            try {
                if (!(codec.decode(record.topic(), record.value()).payload() instanceof DriverLocationUpdatedEvent report)) {
                    throw new EventDecodingException("Not a driver location report");
                }
                reports.add(report);
            } catch (EventDecodingException ex) {
                persistence.persist(reports);
                throw new BatchListenerFailedException(ex.getMessage(), ex, i);
            }
        }
        persistence.persist(reports);
    }
}
