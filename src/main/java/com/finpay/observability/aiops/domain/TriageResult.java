package com.finpay.observability.aiops.domain;

import java.util.UUID;

/**
 * Result of an AI-assisted incident triage: a markdown incident summary, the
 * suspected component and a runbook pointer. Returned synchronously to the
 * alert webhook caller (e.g. Grafana), which owns storing it.
 *
 * <p>When the LLM is unavailable the service still answers with
 * {@code llmAvailable=false} and a fallback markdown so the caller always gets
 * a deterministic response (an alert webhook must not be left unanswered).
 */
public record TriageResult(
        UUID triageId,
        String traceId,
        String suspectedComponent,
        String runbookPointer,
        String markdownSummary,
        boolean llmAvailable
) {
}
