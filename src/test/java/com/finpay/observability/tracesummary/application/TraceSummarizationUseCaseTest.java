package com.finpay.observability.tracesummary.application;

import com.finpay.observability.tracesummary.domain.ChatMessage;
import com.finpay.observability.tracesummary.domain.TraceData;
import com.finpay.observability.tracesummary.domain.TraceNotFoundException;
import com.finpay.observability.tracesummary.domain.TraceRepository;
import com.finpay.observability.tracesummary.domain.TraceSpan;
import com.finpay.observability.tracesummary.domain.TraceSummary;
import com.finpay.observability.tracesummary.domain.TraceSummaryLlmGateway;
import com.finpay.observability.tracesummary.domain.TraceSummaryPromptAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceSummarizationUseCaseTest {

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Mock
    private TraceRepository traceRepository;
    @Mock
    private TraceSummaryLlmGateway llmGateway;

    private TraceSummarizationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TraceSummarizationUseCase(
                traceRepository, llmGateway, new TraceSummaryPromptAssembler());
    }

    @Test
    @SuppressWarnings("unchecked")
    void assembles_prompt_from_spans_and_maps_llm_narrative() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "ERROR", 1250, Map.of()),
                new TraceSpan("2222222222222222", "transfer-service", "transfer.execute", "OK", 340, Map.of())));

        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.of(trace));
        when(llmGateway.complete(any())).thenReturn(Optional.of(
                "**Summary**: transfer failed.\n**Slowest span**: account-service POST /api/v1/transfers (1250 ms)."));

        TraceSummary result = useCase.summarize(TRACE_ID);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmGateway).complete(captor.capture());
        String userPrompt = captor.getValue().get(1).content();
        assertThat(userPrompt).contains("## OTel TRACE (traceId=" + TRACE_ID + ")");
        assertThat(userPrompt).contains("account-service").contains("transfer.execute");
        assertThat(userPrompt).contains("slowest span: account-service POST /api/v1/transfers (1250 ms, ERROR)");

        assertThat(result.traceId()).isEqualTo(TRACE_ID);
        assertThat(result.llmAvailable()).isTrue();
        assertThat(result.narrative()).contains("transfer failed");
        assertThat(result.slowestSpan().spanId()).isEqualTo("1111111111111111");
        assertThat(result.errorSpans()).hasSize(1);
        assertThat(result.spanCount()).isEqualTo(2);
    }

    @Test
    void falls_back_to_facts_only_narrative_when_llm_unavailable() {
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "OK", 200, Map.of())));

        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.of(trace));
        when(llmGateway.complete(any())).thenReturn(Optional.empty());

        TraceSummary result = useCase.summarize(TRACE_ID);

        assertThat(result.llmAvailable()).isFalse();
        assertThat(result.narrative()).contains("AI summary unavailable");
        assertThat(result.narrative()).contains("slowest span: account-service POST /api/v1/transfers (200 ms, OK)");
        assertThat(result.slowestSpan()).isNotNull();
        assertThat(result.errorSpans()).isEmpty();
    }

    @Test
    void throws_trace_not_found_when_trace_is_unknown() {
        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.summarize(TRACE_ID))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining(TRACE_ID);
    }
}