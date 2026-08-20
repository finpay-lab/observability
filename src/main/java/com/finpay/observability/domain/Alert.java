package com.finpay.observability.domain;

import java.time.Instant;
import java.util.Map;

/** Prometheus/Grafana alert model (FP-61). */
public record Alert(String alertName, String severity, String summary,
                    String runbookUrl, Instant firedAt, Map<String, String> labels) {}
