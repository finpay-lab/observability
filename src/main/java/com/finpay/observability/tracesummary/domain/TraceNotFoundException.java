package com.finpay.observability.tracesummary.domain;

/**
 * Raised when a requested traceId is unknown or not yet indexed in the trace
 * store. Mapped to HTTP 404 by the web layer (the domain stays framework-free).
 */
public class TraceNotFoundException extends RuntimeException {

    public TraceNotFoundException(String traceId) {
        super("No trace found for traceId=" + traceId);
    }
}