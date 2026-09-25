package com.rideflow.ai;

/** {@code AI_PROVIDER=disabled}: every call is UNAVAILABLE; trips still get their deterministic observations. */
public class DisabledLlmClient implements LlmClient {

    private static final AIProviderInfo INFO = new AIProviderInfo("disabled", "none");

    @Override
    public LlmResponse complete(LlmRequest request) {
        throw new AIException(AIFailureCode.UNAVAILABLE, "AI is disabled (AI_PROVIDER=disabled)");
    }

    @Override
    public AIProviderInfo info() {
        return INFO;
    }
}
