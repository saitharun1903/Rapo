package com.rideflow.kafka.consumer;

import com.rideflow.kafka.event.EventCodec;
import com.rideflow.kafka.event.ReceivedEvent;
import com.rideflow.service.ai.TripInsightsService;
import com.rideflow.service.event.DomainEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Starts the AI analysis of every completed ride ({@code ride.completed}), in its own consumer group so that
 * AI latency or failures never delay payments or notifications.
 *
 * <p>A model call can take minutes (a local model on CPU, retried once, plus a corrective retry), so this
 * listener fetches one record per poll and allows 15 minutes between polls; with the default of 500 records
 * and 5 minutes, a slow batch would exceed the poll interval, the group would rebalance and the batch would be
 * delivered again.
 */
@Component
public class TripAnalysisEventListener {

    private final EventCodec codec;
    private final TripInsightsService insights;

    public TripAnalysisEventListener(EventCodec codec, TripInsightsService insights) {
        this.codec = codec;
        this.insights = insights;
    }

    @KafkaListener(id = "trip-analysis", idIsGroup = false, groupId = "#{@kafkaNames.group('trip-analysis')}",
            topics = "#{@kafkaNames.topics('RIDE_COMPLETED')}",
            properties = {"max.poll.records=1", "max.poll.interval.ms=900000"})
    public void onRideCompleted(ConsumerRecord<String, String> record) {
        ReceivedEvent<DomainEvent> event = codec.decode(record.topic(), record.value());
        TraceContext.run(event.traceId(),
                () -> insights.analyzeCompletedRide(event.eventId(), event.payload().aggregateId()));
    }
}
