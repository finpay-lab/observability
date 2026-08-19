package com.finpay.observability.tracesummary.domain;

import java.util.List;

/**
 * Result of summarizing an OTel trace via LLM. {@code narrative} is the
 * concise human-readable story (what happened, slowest span, error span)
 * written by the LLM; {@code slowestSpan} / {@code errorSpans} / {@code
 * spanCount} are computed locally and never depend on the LLM.
 *
 * <p>When the LLM is unavailable the service still answers with
 * {@code llmAvailable=false} and a deterministic facts-only narrative so the
 * endpoint always returns a usable summary.
 */
public record TraceSummary(
        String traceId,
        String narrative,
        TraceSpanInfo slowestSpan,
        List<TraceSpanInfo> errorSpans,
        int spanCount,
        boolean llmAvailable
) {

    public TraceSummary {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        errorSpans = errorSpans == null ? List.of() : List.copyOf(errorSpans);
    }
}