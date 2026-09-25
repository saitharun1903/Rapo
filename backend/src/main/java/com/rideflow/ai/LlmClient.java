package com.rideflow.ai;

import java.util.Map;

/**
 * One request to a language model that must answer with JSON matching a schema. Implementations translate
 * provider errors into {@link AIException}s and do not retry; {@link ResilientLlmClient} adds retries, the
 * circuit breaker and the concurrency limit.
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    AIProviderInfo info();

    /**
     * @param jsonSchema JSON schema of the expected answer (constrains decoding where the provider supports it)
     */
    record LlmRequest(String system, String user, Map<String, Object> jsonSchema) {
    }

    record LlmResponse(String text, long inputTokens, long outputTokens) {
    }
}
