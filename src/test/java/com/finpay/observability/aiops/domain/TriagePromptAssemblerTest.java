package com.finpay.observability.aiops.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TriagePromptAssemblerTest {

    private final TriagePromptAssembler assembler = new TriagePromptAssembler();

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Test
    void assembles_system_and_user_message_with_full_context() {
        Alert alert = new Alert(
                "high-error-rate",
                "critical",
                "HTTP 5xx spike detected",
                "error ratio exceeded 5% for 10 minutes",
                TRACE_ID,
                Map.of("service", "account-service", "env", "prod"),
                Map.of("runbook_url", "https://runbooks.finpay.dev/high-error-rate.md"));
        AlertSpec spec = new AlertSpec(
                "high-error-rate",
                "sum(rate(http_server_requests_seconds_count{status=\"5xx\"}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) > 0.05",
                "https://runbooks.finpay.dev/high-error-rate.md");
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "ERROR", 1250,
                        Map.of("http.status_code", "500", "error", "true")),
                new TraceSpan("2222222222222222", "transfer-service", "transfer.execute", "OK", 340, Map.of())));

        List<ChatMessage> messages = assembler.assemble(alert, Optional.of(spec), Optional.of(trace));

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(0).content())
                .contains("on-call SRE assistant")
                .contains("suspected_component");

        String user = messages.get(1).content();
        assertThat(user).contains("high-error-rate");
        assertThat(user).contains("critical");
        assertThat(user).contains("HTTP 5xx spike detected");
        assertThat(user).contains(spec.promql());
        assertThat(user).contains(spec.runbookUrl());
        assertThat(user).contains("service=account-service");

        assertThat(user).contains("## OTel TRACE (traceId=" + TRACE_ID + ")");
        assertThat(user).contains("account-service");
        assertThat(user).contains("POST /api/v1/transfers");
        assertThat(user).contains("ERROR");
        assertThat(user).contains("transfer-service");
    }

    @Test
    void omits_trace_section_when_trace_is_absent() {
        Alert alert = new Alert("high-error-rate", "critical", "boom", null, null, Map.of(), Map.of());

        List<ChatMessage> messages = assembler.assemble(alert, Optional.empty(), Optional.empty());

        String user = messages.get(1).content();
        assertThat(user).contains("No trace available for this alert");
        assertThat(user).doesNotContain("## OTel TRACE");
    }

    @Test
    void omits_promql_and_runbook_when_spec_is_absent() {
        Alert alert = new Alert("unknown-alert", "warning", null, null, null, Map.of(), Map.of());

        List<ChatMessage> messages = assembler.assemble(alert, Optional.empty(), Optional.empty());

        String user = messages.get(1).content();
        assertThat(user).contains("alertname: unknown-alert");
        assertThat(user).doesNotContain("promql:");
        assertThat(user).doesNotContain("runbook:");
    }

    @Test
    void escapes_pipes_in_table_values() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "gateway", "http | route", "OK", 5, Map.of("k", "a|b"))));

        String formatted = assembler.formatTrace(trace);

        assertThat(formatted).doesNotContain("| route");
        assertThat(formatted).contains("http / route");
        assertThat(formatted).contains("a/b");
    }
}