package com.rideflow.ai;

/**
 * A validated AI result with what it cost.
 *
 * @param attempts model calls made, including a corrective retry after an invalid answer
 */
public record AIResult<T>(T value, AIProviderInfo provider, long inputTokens, long outputTokens, long latencyMs,
                          int attempts) {
}
