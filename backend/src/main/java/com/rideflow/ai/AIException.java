package com.rideflow.ai;

import java.time.Duration;
import java.util.Optional;

/**
 * An AI call failed. The code decides what callers do (see {@link AIFailureCode}); the message is for logs only
 * and never contains the prompt, the facts or an API key.
 */
public class AIException extends RuntimeException {

    private final AIFailureCode code;
    private final boolean retryable;
    private final transient Duration retryAfter;

    public AIException(AIFailureCode code, String message) {
        this(code, message, false, null, null);
    }

    public AIException(AIFailureCode code, String message, boolean retryable, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public AIFailureCode code() {
        return code;
    }

    /** Whether the same request may succeed if sent again (server errors, network failures). */
    public boolean retryable() {
        return retryable;
    }

    /** The provider's Retry-After, for {@link AIFailureCode#RATE_LIMITED}. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
