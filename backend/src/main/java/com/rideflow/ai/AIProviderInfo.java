package com.rideflow.ai;

/** Which provider and model produced a result; stored with every analysis and answer. */
public record AIProviderInfo(String provider, String model) {
}
