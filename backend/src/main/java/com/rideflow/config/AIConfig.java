package com.rideflow.config;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.rideflow.ai.AIResponseValidator;
import com.rideflow.ai.AIService;
import com.rideflow.ai.AnthropicLlmClient;
import com.rideflow.ai.DefaultAIService;
import com.rideflow.ai.DisabledLlmClient;
import com.rideflow.ai.LlmClient;
import com.rideflow.ai.OllamaLlmClient;
import com.rideflow.ai.PromptTemplates;
import com.rideflow.ai.ResilientLlmClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Selects the AI provider ({@code AI_PROVIDER}) and wraps it with the resilience layer. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AIProperties.class)
public class AIConfig {

    @Bean
    LlmClient llmClient(AIProperties properties, JsonMapper jsonMapper, MeterRegistry meters) {
        LlmClient provider = switch (properties.provider()) {
            case LOCAL -> new OllamaLlmClient(ollamaHttp(properties.local()), jsonMapper, properties.local());
            case EXTERNAL -> new AnthropicLlmClient(AnthropicOkHttpClient.builder()
                    .apiKey(properties.external().apiKey())
                    .baseUrl(properties.external().baseUrl())
                    .timeout(properties.external().timeout())
                    // Retries belong to ResilientLlmClient, which also feeds the circuit breaker.
                    .maxRetries(0)
                    .build(), properties.external());
            case DISABLED -> new DisabledLlmClient();
        };
        return new ResilientLlmClient(provider, properties.resilience(), duration -> Thread.sleep(duration), meters);
    }

    @Bean
    AIService aiService(LlmClient llmClient, PromptTemplates prompts, AIResponseValidator validator,
                        JsonMapper jsonMapper, MeterRegistry meters) {
        return new DefaultAIService(llmClient, prompts, validator, jsonMapper, meters);
    }

    /** Ollama speaks HTTP/1.1; the JDK client would otherwise attempt an h2c upgrade on every plain-HTTP call. */
    private static HttpClient ollamaHttp(AIProperties.Local settings) {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(settings.connectTimeout()).build();
    }
}
