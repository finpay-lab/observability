package com.finpay.observability.aiops.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the AI Ops incident triage module ({@code finpay.aiops.*}).
 *
 * <p>The LLM API key is BYOK and must come from the secret store / environment
 * ({@code AI_LLM_API_KEY}); a blank key disables the LLM step and the use case
 * falls back to a context-only summary.
 */
@ConfigurationProperties(prefix = "finpay.aiops")
public record AiOpsProperties(
        Llm llm,
        TraceStore traceStore,
        List<AlertSpecConfig> alertSpecs
) {

    public AiOpsProperties {
        if (llm == null) {
            llm = new Llm("https://api.openai.com/v1", "", "gpt-4o-mini", Duration.ofSeconds(30), 2);
        }
        if (traceStore == null) {
            traceStore = new TraceStore("http://otel-collector:16686", Duration.ofSeconds(10));
        }
        if (alertSpecs == null) {
            alertSpecs = List.of();
        }
    }

    public record Llm(String baseUrl, String apiKey, String model, Duration timeout, int maxRetries) {
    }

    public record TraceStore(String baseUrl, Duration timeout) {
    }

    public record AlertSpecConfig(String alertName, String promql, String runbookUrl) {
    }
}