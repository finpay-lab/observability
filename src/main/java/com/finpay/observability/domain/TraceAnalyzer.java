package com.finpay.observability.domain;

import java.util.List;

/**
 * Summarizes a distributed trace for a traceId into a human-readable narrative
 * (FP-62 / AI-5). Pure domain logic: builds the narrative from spans (slowest
 * span, error span) and delegates the prose to the LLM port. Zero impact on
 * money paths. No Spring/OTel imports.
 */
public final class TraceAnalyzer {

    private final LlmSummarizer llm;

    public TraceAnalyzer(LlmSummarizer llm) { this.llm = llm; }

    public record TraceSummary(String traceId, String narrative,
                               String slowestSpan, String errorSpan,
                               boolean llmUsed) {}

    /** Build a debugging narrative from spans for a traceId. */
    public TraceSummary summarize(String traceId, List<Span> spans) {
        Span slowest = spans.stream().max(java.util.Comparator.comparingLong(Span::durationNanos)).orElse(null);
        Span errored = spans.stream().filter(Span::error).findFirst().orElse(null);

        String slowestDesc = slowest == null ? "n/a"
                : slowest.service() + "/" + slowest.operation() + " (" + human(slowest.durationNanos()) + ")";
        String errorDesc = errored == null ? "none" : errored.service() + "/" + errored.operation();

        String prompt = buildPrompt(traceId, spans, slowestDesc, errorDesc);
        String narrative;
        boolean llmUsed = false;
        try {
            narrative = llm.summarize(prompt);
            llmUsed = true;
        } catch (Exception ex) {
            // Deterministic fallback narrative (no LLM).
            narrative = "Trace " + traceId + ": " + spans.size() + " spans. Slowest=" + slowestDesc
                    + ". Error=" + errorDesc + ".";
        }
        return new TraceSummary(traceId, narrative, slowestDesc, errorDesc, llmUsed);
    }

    private String buildPrompt(String traceId, List<Span> spans, String slowest, String error) {
        StringBuilder sb = new StringBuilder("Summarize trace ").append(traceId)
                .append(" with ").append(spans.size()).append(" spans. Slowest: ").append(slowest)
                .append(". Error: ").append(error).append(". Provide a concise debugging narrative.");
        return sb.toString();
    }

    private String human(long nanos) {
        long ms = nanos / 1_000_000;
        return ms + "ms";
    }
}
