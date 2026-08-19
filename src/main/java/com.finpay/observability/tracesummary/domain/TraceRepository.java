package com.finpay.observability.tracesummary.domain;

import java.util.Optional;

/**
 * Fetches a distilled {@link TraceData} for a W3C trace id. Implementation
 * lives in {@code infrastructure/} (Jaeger/OpenSearch query API).
 *
 * <p>Returns {@link Optional#empty()} when the trace is unknown or the store
 * is unreachable; the caller decides how to surface that to the user.
 */
public interface TraceRepository {

    Optional<TraceData> fetch(String traceId);
}