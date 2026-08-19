package com.finpay.observability.tracesummary.domain;

import java.util.Map;

/**
 * A single span of an OTel trace, distilled to the attributes that matter for
 * summarization. {@code status} is the OTel span status ("OK", "ERROR",
 * "UNSET"); {@code attributes} carries a small curated set of key/value pairs
 * (e.g. {@code http.status_code}, {@code error}).
 *
 * <p>Values may be derived from sensitive payloads; callers must never log
 * them (they may still be sent to the LLM provider as part of the prompt).
 */
public record TraceSpan(
        String spanId,
        String serviceName,
        String operationName,
        String status,
        long durationMs,
        Map<String, String> attributes
) {

    public TraceSpan {
        if (spanId == null || spanId.isBlank()) {
            throw new IllegalArgumentException("spanId must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}