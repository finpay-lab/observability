package com.finpay.observability.domain;

import java.util.List;
import java.util.Map;

/**
 * AI Ops incident triage (FP-61 / AI-4). Given a Prometheus/Grafana alert and
 * the related OTel trace, produces a root-cause hypothesis + runbook link via
 * the LLM port. Pure observability sidecar — no money-path impact. No
 * Spring/OTel imports.
 */
public final class IncidentTriage {

    private final LlmSummarizer llm;

    public IncidentTriage(LlmSummarizer llm) { this.llm = llm; }

    public record TriageResult(String alertName, String hypothesis, String suspectedComponent,
                               String runbookUrl, boolean llmUsed) {}

    public TriageResult triage(Alert alert, List<Span> relatedSpans) {
        String suspected = relatedSpans.isEmpty() ? "unknown"
                : relatedSpans.stream().filter(Span::error).map(Span::service).findFirst()
                    .orElse(relatedSpans.get(0).service());
        String prompt = buildPrompt(alert, relatedSpans, suspected);
        String hypothesis;
        boolean llmUsed = false;
        try {
            hypothesis = llm.summarize(prompt);
            llmUsed = true;
        } catch (Exception ex) {
            hypothesis = "Alert '" + alert.alertName() + "' on " + suspected
                    + ". See runbook: " + alert.runbookUrl();
        }
        return new TriageResult(alert.alertName(), hypothesis, suspected, alert.runbookUrl(), llmUsed);
    }

    private String buildPrompt(Alert alert, List<Span> spans, String suspected) {
        return "Alert: " + alert.alertName() + " (" + alert.severity() + "). Suspected component: "
                + suspected + ". Related spans: " + spans.size()
                + ". Produce a root-cause hypothesis and reference the runbook.";
    }
}
