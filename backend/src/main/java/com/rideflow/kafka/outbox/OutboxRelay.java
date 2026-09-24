package com.rideflow.kafka.outbox;

import com.rideflow.config.OutboxProperties;
import com.rideflow.kafka.outbox.OutboxPublisher.BatchResult;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Moves outbox rows to Kafka on a dedicated thread. It runs when a transaction that wrote events commits
 * ({@link #wakeUp()}), and otherwise every {@code rideflow.outbox.poll-interval}, which picks up rows whose
 * wake-up was lost (another instance wrote them, or a send failed). While full batches keep succeeding it
 * continues without waiting. Every instance runs a relay; {@code SKIP LOCKED} keeps them on separate rows.
 *
 * <p>Its own thread rather than {@code @Scheduled}: relaying is part of the event pipeline, not a background
 * sweep, and must keep running when scheduled jobs are switched off (as in the integration tests).
 */
@Component
public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxPublisher publisher;
    private final OutboxProperties properties;
    private final Semaphore wakeUps = new Semaphore(0);
    private volatile boolean running;
    private Thread worker;

    public OutboxRelay(OutboxPublisher publisher, OutboxProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    /** Runs the relay now if it is waiting. Cheap and safe to call from any thread. */
    public void wakeUp() {
        if (wakeUps.availablePermits() == 0) {
            wakeUps.release();
        }
    }

    @Override
    public void start() {
        if (!properties.relayEnabled()) {
            log.info("Outbox relay disabled (rideflow.outbox.relay-enabled=false); events stay in the outbox");
            return;
        }
        running = true;
        worker = Thread.ofVirtual().name("outbox-relay").start(this::relayUntilStopped);
    }

    @Override
    public void stop() {
        running = false;
        wakeUp();
        if (worker != null) {
            try {
                // A batch in flight finishes (or times out) first, so its rows are marked correctly.
                if (!worker.join(properties.sendTimeout().plus(properties.pollInterval()))) {
                    log.warn("Outbox relay did not stop in time; unacknowledged rows will be sent again");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void relayUntilStopped() {
        while (running) {
            Duration wait;
            try {
                BatchResult result = publisher.publishBatch();
                boolean moreWaiting = result.fetched() == properties.batchSize() && result.published() == result.fetched();
                wait = moreWaiting ? Duration.ZERO : properties.pollInterval();
            } catch (RuntimeException ex) {
                log.error("Outbox relay run failed; retrying in {}", properties.errorBackoff(), ex);
                wait = properties.errorBackoff();
            }
            if (!wait.isZero()) {
                awaitWakeUp(wait);
            }
        }
    }

    private void awaitWakeUp(Duration timeout) {
        try {
            if (wakeUps.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                wakeUps.drainPermits();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
