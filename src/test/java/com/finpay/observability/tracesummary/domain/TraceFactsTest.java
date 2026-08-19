package com.finpay.observability.tracesummary.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceFactsTest {

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Test
    void picks_slowest_span_by_duration() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "a", "OK", 100, Map.of()),
                new TraceSpan("2222222222222222", "transfer-service", "b", "OK", 900, Map.of()),
                new TraceSpan("3333333333333333", "ledger-service", "c", "OK", 300, Map.of())));

        TraceFacts facts = TraceFacts.compute(trace);

        assertThat(facts.slowestSpan().spanId()).isEqualTo("2222222222222222");
        assertThat(facts.slowestSpan().durationMs()).isEqualTo(900);
        assertThat(facts.errorSpans()).isEmpty();
        assertThat(facts.spanCount()).isEqualTo(3);
    }

    @Test
    void detects_error_spans_by_status_and_error_attribute() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "a", "ERROR", 100, Map.of()),
                new TraceSpan("2222222222222222", "transfer-service", "b", "UNSET", 50, Map.of("error", "true")),
                new TraceSpan("3333333333333333", "ledger-service", "c", "OK", 10, Map.of())));

        TraceFacts facts = TraceFacts.compute(trace);

        assertThat(facts.errorSpans()).hasSize(2);
        assertThat(facts.errorSpans().get(0).spanId()).isEqualTo("1111111111111111");
        assertThat(facts.errorSpans().get(1).spanId()).isEqualTo("2222222222222222");
        assertThat(facts.slowestSpan().spanId()).isEqualTo("1111111111111111");
    }

    @Test
    void empty_trace_has_no_slowest_or_error_span() {
        TraceFacts facts = TraceFacts.compute(new TraceData(TRACE_ID, List.of()));

        assertThat(facts.slowestSpan()).isNull();
        assertThat(facts.errorSpans()).isEmpty();
        assertThat(facts.spanCount()).isZero();
    }
}