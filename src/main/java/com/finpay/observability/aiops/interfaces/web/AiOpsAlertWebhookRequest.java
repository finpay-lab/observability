package com.finpay.observability.aiops.interfaces.web;

import java.util.Map;

/**
 * Grafana alert notification webhook payload (Grafana Alerting, "webhook"
 * contact point). The controller maps it onto a domain {@code Alert}.
 *
 * <p>Grafana sends the alert identity in {@code labels[alertname]} /
 * {@code alertName}, severity in {@code labels[severity]} / {@code severity},
 * and human-readable context in {@code annotations[summary|description]}. The
 * OTel trace id, when present, travels in
 * {@code annotations[traceId]} (W3C {@code traceparent}).
 *
 * <p>Unknown fields from other alerting tools are ignored by Jackson.
 */
public record AiOpsAlertWebhookRequest(
        String title,
        String state,
        String message,
        String alertName,
        String severity,
        Map<String, String> labels,
        Map<String, String> annotations
) {
}