package com.rideflow.ai;

/** Why an AI call produced no usable result (docs/architecture.md section 12.4). */
public enum AIFailureCode {
    /** The provider did not answer in time. */
    TIMEOUT,
    /** Server error, network failure, or a request the provider rejected. */
    PROVIDER_ERROR,
    /** The provider throttled us, and waiting was not an option. */
    RATE_LIMITED,
    /** The answer was not valid JSON, broke a limit, or stated numbers that are not in the facts. */
    INVALID_RESPONSE,
    /** The model declined to answer. */
    REFUSED,
    /** Too many AI calls in flight on this instance. */
    BUSY,
    /** AI is switched off, or the circuit breaker is open after repeated failures. */
    UNAVAILABLE
}
