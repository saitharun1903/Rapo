package com.rideflow.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.entity.OfferStatus;
import com.rideflow.entity.RideStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RideMetricsTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RideMetrics metrics = new RideMetrics(meters);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void registersEveryStatusAndOutcomeUpFront() {
        // Zero-valued series exist from startup, so dashboards and rate() see them before the first ride.
        assertThat(meters.find("rideflow.rides").counters()).hasSize(RideStatus.values().length);
        assertThat(meters.find("rideflow.offers").counters()).hasSize(OfferStatus.values().length);
        assertThat(meters.find("rideflow.matching.duration").timer()).isNotNull();
    }

    @Test
    void countsOutsideATransactionImmediately() {
        metrics.rideEntered(RideStatus.COMPLETED);
        metrics.offersCreated(3);
        metrics.offersClosed(OfferStatus.EXPIRED, 2);
        metrics.rideMatched(Duration.ofSeconds(42));

        assertThat(rides("completed")).isEqualTo(1);
        assertThat(offers(RideMetrics.OFFER_CREATED)).isEqualTo(3);
        assertThat(offers("expired")).isEqualTo(2);
        assertThat(meters.get("rideflow.matching.duration").timer().totalTime(TimeUnit.SECONDS)).isEqualTo(42);
    }

    @Test
    void countsInATransactionOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.rideEntered(RideStatus.DRIVER_ASSIGNED);
        metrics.offersClosed(OfferStatus.ACCEPTED, 1);
        metrics.rideMatched(Duration.ofSeconds(5));
        assertThat(rides("driver_assigned")).isZero();
        assertThat(offers("accepted")).isZero();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(rides("driver_assigned")).isEqualTo(1);
        assertThat(offers("accepted")).isEqualTo(1);
        assertThat(meters.get("rideflow.matching.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void rolledBackWorkIsNotCounted() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.rideEntered(RideStatus.CANCELLED);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(rides("cancelled")).isZero();
    }

    @Test
    void ignoresEmptyBatchesAndRejectsPendingAsAnOutcome() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.offersCreated(0);
        metrics.offersClosed(OfferStatus.CANCELLED, 0);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        assertThatThrownBy(() -> metrics.offersClosed(OfferStatus.PENDING, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private double rides(String event) {
        return meters.get("rideflow.rides").tag("event", event).counter().count();
    }

    private double offers(String outcome) {
        return meters.get("rideflow.offers").tag("outcome", outcome).counter().count();
    }
}
