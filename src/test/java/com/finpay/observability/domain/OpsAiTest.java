package com.finpay.observability.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceAnalyzerTest {

    static final class FakeLlm implements LlmSummarizer {
        boolean fail = false;
        String out = "LLM narrative";
        @Override public String summarize(String prompt) {
            if (fail) throw new IllegalStateException("boom");
            return out;
        }
    }

    private Span span(String svc, String op, long dur, boolean err) {
        return new Span("s", "p", op, svc, 0, dur, Map.of(), err);
    }

    @Test
    void summarizesWithSlowestAndErrorSpan() {
        List<Span> spans = List.of(
                span("gateway", "POST /v1/transfers", 100_000_000L, false),
                span("ledger", "postEntry", 900_000_000L, false),
                span("transfer", "debit", 50_000_000L, true));
        TraceAnalyzer a = new TraceAnalyzer(new FakeLlm());
        TraceAnalyzer.TraceSummary s = a.summarize("trace-1", spans);
        assertThat(s.slowestSpan()).contains("ledger");
        assertThat(s.errorSpan()).contains("transfer");
        assertThat(s.llmUsed()).isTrue();
        assertThat(s.narrative()).isEqualTo("LLM narrative");
    }

    @Test
    void fallsBackWhenLlmFails() {
        FakeLlm llm = new FakeLlm(); llm.fail = true;
        TraceAnalyzer a = new TraceAnalyzer(llm);
        TraceAnalyzer.TraceSummary s = a.summarize("t2", List.of(span("x", "y", 1, false)));
        assertThat(s.llmUsed()).isFalse();
        assertThat(s.narrative()).contains("Trace t2");
    }
}

class IncidentTriageTest {

    static final class FakeLlm implements LlmSummarizer {
        boolean fail = false;
        @Override public String summarize(String p) { if (fail) throw new IllegalStateException("x"); return "hypothesis"; }
    }

    @Test
    void triagesAlertWithSuspectedComponent() {
        Alert alert = new Alert("HighLatency", "warning", "p99 high", "https://rb/1", null, Map.of());
        List<Span> spans = List.of(new Span("s", "p", "db", "ledger", 0, 1, Map.of(), true));
        IncidentTriage t = new IncidentTriage(new FakeLlm());
        IncidentTriage.TriageResult r = t.triage(alert, spans);
        assertThat(r.suspectedComponent()).isEqualTo("ledger");
        assertThat(r.llmUsed()).isTrue();
    }

    @Test
    void triageFallsBackWithoutLlm() {
        Alert alert = new Alert("X", "crit", "y", "https://rb/2", null, Map.of());
        IncidentTriage r2 = new IncidentTriage(new LlmSummarizer() {
            public String summarize(String p) { throw new IllegalStateException(); }
        });
        IncidentTriage.TriageResult r = r2.triage(alert, List.of());
        assertThat(r.llmUsed()).isFalse();
        assertThat(r.hypothesis()).contains("X");
    }
}
