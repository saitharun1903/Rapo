package com.rideflow.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rideflow.config.AIProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The local provider: a model served by Ollama ({@code POST /api/chat}), for development without an API key
 * and for keeping trip data on the machine. The answer schema is passed as Ollama's {@code format}, which
 * constrains decoding to valid JSON of that shape.
 *
 * <p>The timeout covers the whole call, response body included. A per-request timeout of the HTTP client only
 * covers the wait for response headers, and a real run showed a call running 641 s past a 300 s limit; here the
 * exchange is cancelled when the deadline passes.
 */
public class OllamaLlmClient implements LlmClient {

    static final String PROVIDER = "local";
    private static final String CHAT_PATH = "/api/chat";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR = 500;
    private static final int SUCCESS_MIN = 200;
    private static final int SUCCESS_MAX = 299;

    private final HttpClient http;
    private final JsonMapper json;
    private final AIProperties.Local settings;
    private final URI chatUri;

    public OllamaLlmClient(HttpClient http, JsonMapper json, AIProperties.Local settings) {
        this.http = http;
        this.json = json;
        this.settings = settings;
        this.chatUri = URI.create(settings.baseUrl().replaceAll("/+$", "") + CHAT_PATH);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Map<String, Object> body = Map.of(
                "model", settings.model(),
                "stream", false,
                "format", request.jsonSchema(),
                "options", Map.of("temperature", settings.temperature(), "num_predict", settings.maxOutputTokens()),
                "messages", List.of(
                        Map.of("role", "system", "content", request.system()),
                        Map.of("role", "user", "content", request.user())));
        HttpRequest httpRequest = HttpRequest.newBuilder(chatUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        CompletableFuture<HttpResponse<String>> call = http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response;
        try {
            response = call.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            call.cancel(true);
            throw new AIException(AIFailureCode.TIMEOUT, "Ollama did not answer within " + settings.timeout(),
                    false, null, ex);
        } catch (ExecutionException ex) {
            throw new AIException(AIFailureCode.PROVIDER_ERROR, "Ollama unreachable: " + ex.getCause(), true, null, ex);
        } catch (InterruptedException ex) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new AIException(AIFailureCode.PROVIDER_ERROR, "Interrupted while waiting for Ollama", false, null, ex);
        }
        return toResponse(response);
    }

    @Override
    public AIProviderInfo info() {
        return new AIProviderInfo(PROVIDER, settings.model());
    }

    private LlmResponse toResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == TOO_MANY_REQUESTS) {
            throw new AIException(AIFailureCode.RATE_LIMITED, "Ollama returned 429", false, null, null);
        }
        if (status < SUCCESS_MIN || status > SUCCESS_MAX) {
            // 404 means the model is not pulled: a configuration error that a retry cannot fix.
            throw new AIException(AIFailureCode.PROVIDER_ERROR, "Ollama returned " + status + " for model "
                    + settings.model(), status >= SERVER_ERROR, null, null);
        }
        ChatResponse chat;
        try {
            chat = json.readValue(response.body(), ChatResponse.class);
        } catch (JacksonException ex) {
            throw new AIException(AIFailureCode.PROVIDER_ERROR, "Unreadable Ollama response: " + ex.getOriginalMessage(),
                    true, null, ex);
        }
        if (chat == null || chat.message() == null || chat.message().content() == null) {
            throw new AIException(AIFailureCode.PROVIDER_ERROR, "Ollama returned no message", true, null, null);
        }
        return new LlmResponse(chat.message().content(), chat.promptEvalCount(), chat.evalCount());
    }

    /** The part of Ollama's chat response used here. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(
            Message message,
            @JsonProperty("prompt_eval_count") long promptEvalCount,
            @JsonProperty("eval_count") long evalCount) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String content) {
        }
    }
}
