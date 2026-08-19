package com.finpay.observability.tracesummary.domain;

import java.util.List;

/**
 * A distilled view of an OpenTelemetry trace, reduced to the ordered span list
 * with status, duration and a few key attributes. Keeps infrastructure
 * (Jaeger/OpenSearch) concerns out of the domain.
 */
public record TraceData(String traceId, List<TraceSpan> spans) {

    public TraceData {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        spans = spans == null ? List.of() : List.copyOf(spans);
    }
}