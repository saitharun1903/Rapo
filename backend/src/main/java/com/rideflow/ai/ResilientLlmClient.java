package com.rideflow.ai;

import com.rideflow.config.AIProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a provider client with the failure handling of docs/architecture.md section 12.4, outermost first:
 * <ol>
 *   <li><b>Bulkhead:</b> at most {@code maxConcurrentCalls} calls in flight; more fail at once with BUSY instead
 *       of queueing behind a slow model.</li>
 *   <li><b>Circuit breaker:</b> when {@code failureRateThreshold}% of the last {@code slidingWindowSize} calls
 *       failed, calls fail at once with UNAVAILABLE for {@code openDuration}, then a trial call decides.</li>
 *   <li><b>Retry:</b> server errors and network failures are retried with exponential backoff, up to
 *       {@code maxAttempts}. A 429 is retried once after the provider's Retry-After when that is short enough.
 *       Timeouts are not retried: a model that did not answer in time will usually not answer the next call
 *       in time either, and the caller has already waited.</li>
 * </ol>
 * Timeouts, server errors and rate limits count as circuit-breaker failures; refusals and rejected requests do
 * not, because the provider was reachable and answered.
 */
public final class ResilientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmClient.class);

    private final LlmClient delegate;
    private final AIProperties.Resilience settings;
    private final Sleeper sleeper;
    private final Bulkhead bulkhead;
    private final CircuitBreaker circuitBreaker;

    /** Waits between retries; replaced in tests. */
    @FunctionalInterface
    public interface Sleeper {

        void sleep(Duration duration) throws InterruptedException;
    }

    public ResilientLlmClient(LlmClient delegate, AIProperties.Resilience settings, Sleeper sleeper,
                              MeterRegistry meters) {
        this.delegate = delegate;
        this.settings = settings;
        this.sleeper = sleeper;
        this.bulkhead = Bulkhead.of("ai", BulkheadConfig.custom()
                .maxConcurrentCalls(settings.maxConcurrentCalls())
                .maxWaitDuration(Duration.ZERO)
                .build());
        this.circuitBreaker = CircuitBreaker.of("ai", CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.slidingWindowSize())
                .waitDurationInOpenState(settings.openDuration())
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("AI circuit breaker {}", event.getStateTransition()));
        Gauge.builder("rideflow.ai.calls.active", this, ResilientLlmClient::activeCalls)
                .description("AI calls in flight on this instance")
                .register(meters);
        Gauge.builder("rideflow.ai.circuit.open", circuitBreaker,
                        breaker -> breaker.getState() == CircuitBreaker.State.OPEN ? 1 : 0)
                .description("1 while AI calls are refused after repeated provider failures")
                .register(meters);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (!bulkhead.tryAcquirePermission()) {
            throw new AIException(AIFailureCode.BUSY, "Too many AI calls in progress");
        }
        try {
            if (!circuitBreaker.tryAcquirePermission()) {
                throw new AIException(AIFailureCode.UNAVAILABLE, "AI provider circuit is open after repeated failures");
            }
            long start = System.nanoTime();
            try {
                LlmResponse response = withRetries(request);
                circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                return response;
            } catch (AIException ex) {
                if (countsAsOutage(ex.code())) {
                    circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
                } else {
                    circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                }
                throw ex;
            }
        } finally {
            bulkhead.onComplete();
        }
    }

    @Override
    public AIProviderInfo info() {
        return delegate.info();
    }

    public int activeCalls() {
        return settings.maxConcurrentCalls() - bulkhead.getMetrics().getAvailableConcurrentCalls();
    }

    private LlmResponse withRetries(LlmRequest request) {
        boolean rateLimitWaited = false;
        int attempt = 1;
        while (true) {
            try {
                return delegate.complete(request);
            } catch (AIException ex) {
                Duration wait = waitBeforeRetry(ex, attempt, rateLimitWaited);
                if (wait == null) {
                    throw ex;
                }
                if (ex.code() == AIFailureCode.RATE_LIMITED) {
                    rateLimitWaited = true;
                } else {
                    attempt++;
                }
                log.info("AI call failed ({}), retrying in {}", ex.code(), wait);
                pause(wait, ex);
            }
        }
    }

    /** @return how long to wait before retrying, or {@code null} to give up */
    private Duration waitBeforeRetry(AIException ex, int attempt, boolean rateLimitWaited) {
        if (ex.code() == AIFailureCode.RATE_LIMITED) {
            return ex.retryAfter()
                    .filter(retryAfter -> !rateLimitWaited && retryAfter.compareTo(settings.maxRetryAfter()) <= 0)
                    .orElse(null);
        }
        if (ex.retryable() && attempt < settings.maxAttempts()) {
            return settings.backoff().multipliedBy(1L << (attempt - 1));
        }
        return null;
    }

    private void pause(Duration wait, AIException cause) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    private static boolean countsAsOutage(AIFailureCode code) {
        return code == AIFailureCode.TIMEOUT || code == AIFailureCode.PROVIDER_ERROR || code == AIFailureCode.RATE_LIMITED;
    }
}
