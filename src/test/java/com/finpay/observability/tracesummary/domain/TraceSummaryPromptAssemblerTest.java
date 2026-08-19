package com.finpay.observability.tracesummary.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceSummaryPromptAssemblerTest {

    private final TraceSummaryPromptAssembler assembler = new TraceSummaryPromptAssembler();

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Test
    void assembles_system_and_user_messages_from_trace_spans() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "ERROR", 1250,
                        Map.of("http.status_code", "500")),
                new TraceSpan("2222222222222222", "transfer-service", "transfer.execute", "OK", 340, Map.of()),
                new TraceSpan("3333333333333333", "ledger-service", "ledger.post", "OK", 120, Map.of())));
        TraceFacts facts = TraceFacts.compute(trace);

        List<ChatMessage> messages = assembler.assemble(trace, facts);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(0).content())
                .contains("OpenTelemetry distributed trace")
                .contains("Never invent")
                .contains("sensitive payloads");

        String user = messages.get(1).content();
        assertThat(user).contains("## OTel TRACE (traceId=" + TRACE_ID + ")");
        assertThat(user).contains("account-service");
        assertThat(user).contains("POST /api/v1/transfers");
        assertThat(user).contains("transfer.execute");
        assertThat(user).contains("ledger.post");
        assertThat(user).contains("ERROR");

        // Deterministic facts are embedded in the prompt for the LLM to narrate.
        assertThat(user).contains("slowest span: account-service POST /api/v1/transfers (1250 ms, ERROR)");
        assertThat(user).contains("error spans:");
        assertThat(user).contains("- account-service POST /api/v1/transfers (1250 ms, ERROR)");
    }

    @Test
    void reports_no_errors_when_all_spans_are_ok() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "OK", 200, Map.of()),
                new TraceSpan("2222222222222222", "transfer-service", "transfer.execute", "OK", 80, Map.of())));
        TraceFacts facts = TraceFacts.compute(trace);

        List<ChatMessage> messages = assembler.assemble(trace, facts);

        assertThat(messages.get(1).content())
                .contains("slowest span: account-service POST /api/v1/transfers (200 ms, OK)")
                .contains("error spans: none");
    }

    @Test
    void escapes_pipes_in_table_values() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "gateway", "http | route", "OK", 5, Map.of("k", "a|b"))));

        String formatted = assembler.formatTraceTable(trace);

        assertThat(formatted).doesNotContain("| route");
        assertThat(formatted).contains("http / route");
        assertThat(formatted).contains("a/b");
    }
}