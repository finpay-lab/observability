package com.finpay.observability.domain;

import java.time.Instant;
import java.util.Map;

/** OTel span model (FP-62). */
public record Span(String spanId, String parentSpanId, String operation,
                   String service, long startEpochNanos, long durationNanos,
                   Map<String, String> attributes, boolean error) {}
