package com.finpay.observability.tracesummary.infrastructure;

import com.finpay.observability.tracesummary.domain.ChatMessage;
import com.finpay.observability.tracesummary.domain.TraceSummaryLlmGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * OpenAI-compatible chat-completions {@link TraceSummaryLlmGateway} ({@code POST
 * {baseUrl}/chat/completions}) with a BYOK API key.
 *
 * <p>Remote-dependency policy (Rule 8): a connect timeout, a per-request
 * timeout and a bounded retry with backoff for transient failures (429/5xx,
 * I/O errors). Any failure returns {@link Optional#empty()} so the use case
 * falls back to a deterministic facts-only summary instead of failing the
 * request.
 *
 * <p>Privacy: the request body carries span data to the LLM provider, but only
 * the HTTP status and a short error message are ever logged — never span
 * payloads (PII policy).
 */
@Component
public class HttpTraceSummaryLlmGateway implements TraceSummaryLlmGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpTraceSummaryLlmGateway.class);

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String chatUrl;
    private final TraceSummaryProperties.Llm llm;

    public HttpTraceSummaryLlmGateway(TraceSummaryProperties properties, ObjectMapper objectMapper) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        this.llm = properties.llm();
        this.chatUrl = stripTrailingSlash(llm.baseUrl()) + "/chat/completions";
    }

    @Override
    public Optional<String> complete(List<ChatMessage> messages) {
        if (llm.apiKey() == null || llm.apiKey().isBlank()) {
            log.warn("AI_LLM_API_KEY is not configured; skipping LLM summarisation");
            return Optional.empty();
        }
        ChatCompletionRequest request = new ChatCompletionRequest(llm.model(), messages);

        int maxAttempts = Math.max(1, llm.maxRetries() + 1);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                sleepBackoff(attempt);
            }
            try {
                String body = objectMapper.writeValueAsString(request);
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(chatUrl))
                        .timeout(llm.timeout())
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + llm.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 429 || status >= 500) {
                    if (attempt + 1 < maxAttempts) {
                        log.warn("LLM returned {}; retrying (attempt {})", status, attempt + 2);
                        continue;
                    }
                    log.warn("LLM returned {} after {} attempts", status, maxAttempts);
                    return Optional.empty();
                }
                if (status != 200) {
                    log.warn("LLM returned unexpected status {}", status);
                    return Optional.empty();
                }
                return parseNarrative(response.body());
            } catch (IOException e) {
                if (attempt + 1 < maxAttempts) {
                    log.warn("LLM call failed (attempt {}): {}", attempt + 1, e.getMessage());
                    continue;
                }
                log.warn("LLM call failed after {} attempts: {}", maxAttempts, e.getMessage());
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while calling the LLM");
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<String> parseNarrative(String responseBody) {
        try {
            ChatCompletionResponse response =
                    objectMapper.readValue(responseBody, ChatCompletionResponse.class);
            if (response.choices() == null || response.choices().isEmpty()) {
                log.warn("LLM returned no choices");
                return Optional.empty();
            }
            String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                log.warn("LLM returned empty content");
                return Optional.empty();
            }
            return Optional.of(stripCodeFence(content));
        } catch (IOException e) {
            log.warn("Failed to parse LLM answer: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Duration.ofMillis(500L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // --- OpenAI-compatible chat completions DTOs ---

    record ChatCompletionRequest(String model, List<ChatMessage> messages) {
    }

    record ChatCompletionResponse(List<Choice> choices) {
    }

    record Choice(Message message) {
    }

    record Message(String content) {
    }
}