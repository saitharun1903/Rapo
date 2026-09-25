package com.rideflow.monitoring;

import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.RideStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ride pipeline metrics (docs/architecture.md section 15). A count is taken only once the transaction that
 * caused it commits, so work that rolls back (an accept that lost the race, a failed flush) is never counted.
 */
@Component
public class RideMetrics {

    /** Tag value for newly created offers; every other outcome is the offer's final status. */
    static final String OFFER_CREATED = "created";

    private final Map<RideStatus, Counter> transitions = new EnumMap<>(RideStatus.class);
    private final Map<OfferStatus, Counter> offersClosed = new EnumMap<>(OfferStatus.class);
    private final Counter offersCreated;
    private final Timer matching;

    public RideMetrics(MeterRegistry meters) {
        for (RideStatus status : RideStatus.values()) {
            transitions.put(status, Counter.builder("rideflow.rides")
                    .description("Ride status changes, by the status entered")
                    .tag("event", tagValue(status))
                    .register(meters));
        }
        for (OfferStatus status : OfferStatus.values()) {
            if (status != OfferStatus.PENDING) {
                offersClosed.put(status, offerCounter(meters, tagValue(status)));
            }
        }
        this.offersCreated = offerCounter(meters, OFFER_CREATED);
        this.matching = Timer.builder("rideflow.matching.duration")
                .description("Time from a ride request to a driver accepting it")
                .register(meters);
    }

    public void rideEntered(RideStatus status) {
        afterCommit(transitions.get(status)::increment);
    }

    /** {@code waited}: request to acceptance. A re-dispatched ride counts the time since its original request. */
    public void rideMatched(Duration waited) {
        afterCommit(() -> matching.record(waited));
    }

    public void offersCreated(int count) {
        if (count > 0) {
            afterCommit(() -> offersCreated.increment(count));
        }
    }

    /** {@code outcome} is the status the offers left PENDING for. */
    public void offersClosed(OfferStatus outcome, int count) {
        if (outcome == OfferStatus.PENDING) {
            throw new IllegalArgumentException("A closed offer is no longer pending");
        }
        if (count > 0) {
            afterCommit(() -> offersClosed.get(outcome).increment(count));
        }
    }

    private static Counter offerCounter(MeterRegistry meters, String outcome) {
        return Counter.builder("rideflow.offers")
                .description("Ride offers created, and how they ended")
                .tag("outcome", outcome)
                .register(meters);
    }

    private static String tagValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static void afterCommit(Runnable count) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            count.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                count.run();
            }
        });
    }
}
