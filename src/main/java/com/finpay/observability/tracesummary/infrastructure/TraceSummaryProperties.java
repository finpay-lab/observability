package com.finpay.observability.tracesummary.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the trace summarization module ({@code finpay.tracesummary.*}).
 *
 * <p>The LLM API key is BYOK and must come from the secret store / environment
 * ({@code AI_LLM_API_KEY}); a blank key disables the LLM step and the use case
 * falls back to a deterministic facts-only summary.
 */
@ConfigurationProperties(prefix = "finpay.tracesummary")
public record TraceSummaryProperties(Llm llm, TraceStore traceStore) {

    public TraceSummaryProperties {
        if (llm == null) {
            llm = new Llm("https://api.openai.com/v1", "", "gpt-4o-mini", Duration.ofSeconds(30), 2);
        }
        if (traceStore == null) {
            traceStore = new TraceStore("http://otel-collector:16686", Duration.ofSeconds(10));
        }
    }

    public record Llm(String baseUrl, String apiKey, String model, Duration timeout, int maxRetries) {
    }

    public record TraceStore(String baseUrl, Duration timeout) {
    }
}