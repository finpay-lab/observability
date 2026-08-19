package com.finpay.observability.aiops.domain;

import java.util.Optional;

/**
 * Fetches a distilled {@link TraceData} for a W3C trace id. Implementation
 * lives in {@code infrastructure/} (Jaeger/OpenSearch query API).
 */
public interface TraceRepository {

    Optional<TraceData> fetch(String traceId);
}
