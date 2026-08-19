package com.finpay.observability.aiops.domain;

/**
 * Structured answer produced by the LLM from an alert + trace. The gateway is
 * responsible for decoding the provider's JSON response into this record.
 */
public record TriageAnswer(
        String hypothesis,
        String suspectedComponent,
        String summaryMarkdown
) {
}
