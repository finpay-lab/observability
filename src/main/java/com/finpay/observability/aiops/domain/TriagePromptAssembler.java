package com.finpay.observability.aiops.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles the LLM prompt (system + user messages) for AI incident triage.
 *
 * <p>Pure function, no I/O and no framework imports — this is the unit-tested
 * core of the feature: given an alert, its alert spec and (optionally) a
 * trace, the prompt must contain all the context the LLM needs to produce a
 * root-cause hypothesis, and nothing else.
 */
public final class TriagePromptAssembler {

    public static final String SYSTEM_ROLE =
            "You are an on-call SRE assistant for the FinPay platform. "
                    + "Given a monitoring alert and an optional OpenTelemetry "
                    + "trace, produce a root-cause hypothesis and a concise "
                    + "incident summary for the on-call engineer.";

    public static final String OUTPUT_SCHEMA =
            "Respond with JSON only, exactly matching this schema:\n"
                    + "{\n"
                    + "  \"hypothesis\": \"root cause hypothesis, 2-4 sentences\",\n"
                    + "  \"suspected_component\": \"service or component name\",\n"
                    + "  \"summary_markdown\": \"markdown incident summary with ## sections and bullet lists\"\n"
                    + "}";

    public List<ChatMessage> assemble(
            Alert alert,
            Optional<AlertSpec> spec,
            Optional<TraceData> trace
    ) {
        return List.of(
                new ChatMessage("system", SYSTEM_ROLE + "\n\n" + OUTPUT_SCHEMA),
                new ChatMessage("user", buildUserMessage(alert, spec, trace)));
    }

    private String buildUserMessage(Alert alert, Optional<AlertSpec> spec, Optional<TraceData> trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ALERT\n");
        sb.append("- alertname: ").append(alert.alertName()).append('\n');
        sb.append("- severity: ").append(alert.severity()).append('\n');
        if (notBlank(alert.summary())) {
            sb.append("- summary: ").append(alert.summary()).append('\n');
        }
        if (notBlank(alert.description())) {
            sb.append("- description: ").append(alert.description()).append('\n');
        }
        if (!alert.labels().isEmpty()) {
            sb.append("- labels: ").append(formatLabels(alert.labels())).append('\n');
        }
        spec.ifPresent(s -> {
            sb.append("- promql: `").append(s.promql()).append("`\n");
            sb.append("- runbook: ").append(s.runbookUrl()).append('\n');
        });

        trace.ifPresentOrElse(
                t -> sb.append('\n').append(formatTrace(t)),
                () -> sb.append("\n## TRACE\nNo trace available for this alert (no traceId in the alert payload)."));

        sb.append("\n\nAnalyse the alert and trace above and produce the requested JSON root-cause analysis.");
        return sb.toString();
    }

    /**
     * Renders a distilled trace as a markdown table: span service, operation,
     * status, duration and key attributes.
     */
    String formatTrace(TraceData trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("## OTel TRACE (traceId=").append(trace.traceId()).append(")\n");
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

    private String formatLabels(Map<String, String> labels) {
        StringBuilder sb = new StringBuilder();
        labels.forEach((k, v) -> sb.append(k).append('=').append(v).append(", "));
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    private String formatAttributes(Map<String, String> attributes) {
        StringBuilder sb = new StringBuilder();
        attributes.forEach((k, v) -> sb.append(k).append('=').append(v).append(", "));
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nbsp(String value) {
        if (value == null) {
            return "";
        }
        // Avoid breaking the markdown table when values contain pipes.
        return value.replace('|', '/');
    }
}
