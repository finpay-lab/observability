package com.finpay.observability.aiops.domain;

import java.util.Map;

/**
 * A single span of an OTel trace, reduced to the attributes relevant for
 * triage. {@code status} is the OTel span status ("OK", "ERROR", "UNSET");
 * {@code attributes} carries a small curated set of key/value attributes
 * (e.g. {@code http.status_code}, {@code error}).
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
