package com.finpay.observability.tracesummary.domain;

import java.util.List;

/**
 * Assembles the LLM prompt (system + user messages) for trace summarization.
 *
 * <p>Pure function, no I/O and no framework imports — this is the unit-tested
 * core of the feature: given a distilled OTel trace and its locally computed
 * facts, the prompt must contain all the context the LLM needs to write the
 * narrative, and nothing else.
 *
 * <p>Privacy: span attributes are included in the prompt (they are sent to the
 * LLM provider), but the assembler never logs them and instructs the model not
 * to echo raw payloads.
 */
public final class TraceSummaryPromptAssembler {

    public static final String SYSTEM_ROLE =
            "You are a backend engineer at FinPay reading an OpenTelemetry distributed trace. "
                    + "Write a concise, factual narrative of what happened during the request, for an "
                    + "on-call engineer: the flow of calls across services, the slowest span and why it "
                    + "stands out, and any error span. Never invent spans, services or attributes that "
                    + "are not present in the trace. Span attributes may contain sensitive payloads: "
                    + "do not echo raw values, only service names, operations, statuses and durations.";

    public static final String OUTPUT_INSTRUCTIONS =
            "Respond with plain markdown text only (3-8 sentences), structured as:\n"
                    + "- a one-line **Summary**\n"
                    + "- a short **What happened** walk-through of the span flow\n"
                    + "- a **Slowest span** bullet referencing the slowest span\n"
                    + "- an **Errors** bullet listing the error spans, or \"no errors\"\n"
                    + "Refer to spans as `service operation`. Do not wrap the answer in a code fence.";

    public List<ChatMessage> assemble(TraceData trace, TraceFacts facts) {
        return List.of(
                new ChatMessage("system", SYSTEM_ROLE + "\n\n" + OUTPUT_INSTRUCTIONS),
                new ChatMessage("user", buildUserMessage(trace, facts)));
    }

    private String buildUserMessage(TraceData trace, TraceFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("## OTel TRACE (traceId=").append(trace.traceId()).append(")\n");
        sb.append(formatTraceTable(trace));
        sb.append('\n');
        sb.append("slowest span: ").append(formatSpan(facts.slowestSpan())).append('\n');
        if (facts.errorSpans().isEmpty()) {
            sb.append("error spans: none\n");
        } else {
            sb.append("error spans:\n");
            for (TraceSpanInfo span : facts.errorSpans()) {
                sb.append("- ").append(formatSpan(span)).append('\n');
            }
        }
        sb.append("\nWrite the concise narrative now.");
        return sb.toString();
    }

    /**
     * Renders a distilled trace as a markdown table: span service, operation,
     * status, duration and key attributes.
     */
    String formatTraceTable(TraceData trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("| # | service | operation | status | duration_ms | attributes |\n");
        sb.append("|---|---------|-----------|--------|-------------|------------|\n");
        int i = 1;
        for (TraceSpan span : trace.spans()) {
            sb.append("| ").append(i++)
                    .append(" | ").append(nbsp(span.serviceName()))
                    .append(" | ").append(nbsp(span.operationName()))
                    .append(" | ").append(nbsp(span.status()))
                    .append(" | ").append(span.durationMs())
                    .append(" | ").append(nbsp(formatAttributes(span.attributes())))
                    .append(" |\n");
        }
        return sb.toString();
    }

    private String formatSpan(TraceSpanInfo span) {
        if (span == null) {
            return "none";
        }
        return span.serviceName() + " " + span.operationName()
                + " (" + span.durationMs() + " ms, " + span.status() + ")";
    }

    private static String formatAttributes(java.util.Map<String, String> attributes) {
        StringBuilder sb = new StringBuilder();
        attributes.forEach((k, v) -> sb.append(k).append('=').append(v).append(", "));
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    private static String nbsp(String value) {
        if (value == null) {
            return "";
        }
        // Avoid breaking the markdown table when values contain pipes.
        return value.replace('|', '/');
    }
}