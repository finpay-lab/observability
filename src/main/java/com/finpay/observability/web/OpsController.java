package com.finpay.observability.web;

import com.finpay.observability.domain.IncidentTriage;
import com.finpay.observability.domain.LlmSummarizer;
import com.finpay.observability.domain.Span;
import com.finpay.observability.domain.TraceAnalyzer;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** AI ops endpoints (FP-61/62). No money-path impact. */
@RestController
@RequestMapping("/api/v1")
public class OpsController {

    private final TraceAnalyzer traceAnalyzer;
    private final IncidentTriage triage;

    public OpsController(TraceAnalyzer traceAnalyzer, IncidentTriage triage) {
        this.traceAnalyzer = traceAnalyzer;
        this.triage = triage;
    }

    /** FP-62: summarize a trace for a traceId. */
    @GetMapping("/trace/{traceId}/summary")
    public TraceSummaryResponse summarize(@PathVariable String traceId,
                                          @RequestBody(required = false) List<Span> spans) {
        TraceAnalyzer.TraceSummary s = traceAnalyzer.summarize(traceId, spans == null ? List.of() : spans);
        return new TraceSummaryResponse(s.traceId(), s.narrative(), s.slowestSpan(), s.errorSpan(), s.llmUsed());
    }

    /** FP-61: alert webhook -> triage with related spans. */
    @PostMapping("/alert/triage")
    public TriageResponse triage(@RequestBody TriageRequest req) {
        IncidentTriage.TriageResult r = triage.triage(req.alert(), req.spans());
        return new TriageResponse(r.alertName(), r.hypothesis(), r.suspectedComponent(), r.runbookUrl(), r.llmUsed());
    }

    public record TraceSummaryResponse(String traceId, String narrative, String slowestSpan, String errorSpan, boolean llmUsed) {}
    public record TriageRequest(com.finpay.observability.domain.Alert alert, List<Span> spans) {}
    public record TriageResponse(String alertName, String hypothesis, String suspectedComponent, String runbookUrl, boolean llmUsed) {}
}
