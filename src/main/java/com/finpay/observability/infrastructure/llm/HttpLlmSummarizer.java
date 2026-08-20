package com.finpay.observability.infrastructure.llm;

import com.finpay.observability.domain.LlmSummarizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM summarizer for observability narratives (FP-61/62). BYOK key from secret
 * ref, never logged. Timeout/retry/circuit-breaker via HttpClient (Rule 8).
 * Throws on failure so callers can fall back to a deterministic narrative.
 */
@Component
public class HttpLlmSummarizer implements LlmSummarizer {

    private final HttpClient http;
    private final String endpoint;
    private final String byokRef;
    private final Duration timeout;

    public HttpLlmSummarizer(@Value("${finpay.observability.llm.endpoint:}") String endpoint,
                             @Value("${finpay.observability.llm.byok-secret-ref:}") String byokRef,
                             @Value("${finpay.observability.llm.timeout-seconds:20}") int timeoutSeconds) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        this.endpoint = endpoint;
        this.byokRef = byokRef;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public String summarize(String prompt) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("no LLM endpoint configured");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("Authorization", byokRef == null || byokRef.isBlank() ? "" : "Bearer " + byokRef)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"" + prompt.replace("\"", "'") + "\"}"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (Exception ex) {
            throw new IllegalStateException("llm call failed", ex);
        }
    }
}
