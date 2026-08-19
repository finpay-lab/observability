package com.finpay.observability.aiops.domain;

/**
 * Known properties of an alert rule: the PromQL expression that fired it and
 * the runbook an on-call engineer should follow. Resolved from configuration
 * by {@link AlertSpecRepository}.
 */
public record AlertSpec(String alertName, String promql, String runbookUrl) {
}
