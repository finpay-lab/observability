package com.finpay.observability.tracesummary.domain;

/**
 * Immutable projection of a {@link TraceSpan} without its attribute map, safe
 * to return over HTTP. The attributes stay internal so potentially sensitive
 * span values never leak into responses.
 */
public record TraceSpanInfo(
        String spanId,
        String serviceName,
        String operationName,
        String status,
        long durationMs
) {

    public static TraceSpanInfo from(TraceSpan span) {
        return new TraceSpanInfo(
                span.spanId(),
                span.serviceName(),
                span.operationName(),
                span.status(),
                span.durationMs());
    }
}