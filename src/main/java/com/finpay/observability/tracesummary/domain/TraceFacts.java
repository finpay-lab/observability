package com.finpay.observability.tracesummary.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic facts derived from a {@link TraceData} before any LLM call:
 * the slowest span, every error span and the total span count. Computed
 * locally so the summary never depends on an LLM for the numbers — the LLM
 * only narrates them.
 */
public record TraceFacts(
        TraceSpanInfo slowestSpan,
        List<TraceSpanInfo> errorSpans,
        int spanCount
) {

    public TraceFacts {
        errorSpans = errorSpans == null ? List.of() : List.copyOf(errorSpans);
    }

    public static TraceFacts compute(TraceData trace) {
        TraceSpanInfo slowest = null;
        long slowestDurationMs = -1;
        List<TraceSpanInfo> errors = new ArrayList<>();
        for (TraceSpan span : trace.spans()) {
            if (span.durationMs() > slowestDurationMs) {
                slowestDurationMs = span.durationMs();
                slowest = TraceSpanInfo.from(span);
            }
            if (isError(span)) {
                errors.add(TraceSpanInfo.from(span));
            }
        }
        return new TraceFacts(slowest, errors, trace.spans().size());
    }

    private static boolean isError(TraceSpan span) {
        if ("ERROR".equalsIgnoreCase(span.status())) {
            return true;
        }
        String error = span.attributes().get("error");
        return error != null && "true".equalsIgnoreCase(error);
    }
}