package com.finpay.observability.aiops.domain;

import java.util.Map;

/**
 * An incoming monitoring alert that triggers AI-assisted incident triage.
 *
 * <p>Transport-agnostic: the web layer maps the Grafana/Prometheus webhook
 * payload onto this value object. {@code traceId} is the W3C trace id carried
 * by the alert payload so the corresponding OTel trace can be pulled.
 */
public record Alert(
        String alertName,
        String severity,
        String summary,
        String description,
        String traceId,
        Map<String, String> labels,
        Map<String, String> annotations
) {

    public Alert {
        if (alertName == null || alertName.isBlank()) {
            throw new IllegalArgumentException("alertName must not be blank");
        }
        if (severity == null || severity.isBlank()) {
            severity = "unknown";
        }
        // W3C trace ids are 32 hex chars. Anything else is not a usable trace
        // id and is ignored (also keeps it safe to interpolate into URLs).
        if (traceId != null && !traceId.matches("[0-9a-fA-F]{32}")) {
            traceId = null;
        }
        if (labels == null) {
            labels = Map.of();
        }
        if (annotations == null) {
            annotations = Map.of();
        }
    }
}
