package com.finpay.observability.aiops.application;

import com.finpay.observability.aiops.domain.Alert;
import com.finpay.observability.aiops.domain.AlertSpec;
import com.finpay.observability.aiops.domain.AlertSpecRepository;
import com.finpay.observability.aiops.domain.ChatMessage;
import com.finpay.observability.aiops.domain.LlmGateway;
import com.finpay.observability.aiops.domain.TraceData;
import com.finpay.observability.aiops.domain.TraceRepository;
import com.finpay.observability.aiops.domain.TraceSpan;
import com.finpay.observability.aiops.domain.TriageAnswer;
import com.finpay.observability.aiops.domain.TriagePromptAssembler;
import com.finpay.observability.aiops.domain.TriageResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentTriageUseCaseTest {

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Mock
    private AlertSpecRepository alertSpecRepository;
    @Mock
    private TraceRepository traceRepository;
    @Mock
    private LlmGateway llmGateway;

    private IncidentTriageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new IncidentTriageUseCase(alertSpecRepository, traceRepository, llmGateway, new TriagePromptAssembler());
    }

    @Test
    void assembles_prompt_from_alert_and_trace_and_maps_llm_answer() {
        Alert alert = new Alert("high-error-rate", "critical", "5xx spike", null, TRACE_ID, Map.of(), Map.of());
        AlertSpec spec = new AlertSpec("high-error-rate", "sum(rate(http_server_requests_seconds_count[5m])) > 0.05",
                "https://runbooks.finpay.dev/high-error-rate.md");
        TraceData trace = new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "ERROR", 1250, Map.of())));

        when(alertSpecRepository.findByAlertName("high-error-rate")).thenReturn(Optional.of(spec));
        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.of(trace));
        when(llmGateway.complete(any())).thenReturn(Optional.of(new TriageAnswer(
                "Database saturation",
                "account-db",
                "## Root cause\nConnection pool exhaustion in account-db.")));

        TriageResult result = useCase.triage(alert);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmGateway).complete(captor.capture());
        String userPrompt = captor.getValue().get(1).content();
        assertThat(userPrompt).contains("high-error-rate").contains(spec.promql());
        assertThat(userPrompt).contains("account-service").contains("POST /api/v1/transfers").contains("ERROR");

        assertThat(result.suspectedComponent()).isEqualTo("account-db");
        assertThat(result.runbookPointer()).isEqualTo(spec.runbookUrl());
        assertThat(result.markdownSummary()).contains("account-db");
        assertThat(result.traceId()).isEqualTo(TRACE_ID);
        assertThat(result.triageId()).isNotNull();
        assertThat(result.llmAvailable()).isTrue();
    }

    @Test
    void falls_back_to_context_only_result_when_llm_unavailable() {
        Alert alert = new Alert("high-error-rate", "critical", "5xx spike", null, TRACE_ID, Map.of(), Map.of());
        AlertSpec spec = new AlertSpec("high-error-rate", "sum(rate(...))", "https://runbooks.finpay.dev/high-error-rate.md");

        when(alertSpecRepository.findByAlertName("high-error-rate")).thenReturn(Optional.of(spec));
        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.empty());
        when(llmGateway.complete(any())).thenReturn(Optional.empty());

        TriageResult result = useCase.triage(alert);

        assertThat(result.llmAvailable()).isFalse();
        assertThat(result.suspectedComponent()).isNull();
        assertThat(result.runbookPointer()).isEqualTo(spec.runbookUrl());
        assertThat(result.markdownSummary()).contains("AI Triage unavailable");
        assertThat(result.markdownSummary()).contains("high-error-rate");
    }

    @Test
    void proceeds_without_trace_when_trace_id_is_absent() {
        Alert alert = new Alert("high-error-rate", "warning", null, null, null, Map.of(), Map.of());

        when(alertSpecRepository.findByAlertName("high-error-rate")).thenReturn(Optional.empty());
        when(llmGateway.complete(any())).thenReturn(Optional.of(new TriageAnswer("h", "account-service", "md")));

        TriageResult result = useCase.triage(alert);

        verify(traceRepository, never()).fetch(any());
        assertThat(result.traceId()).isNull();
        assertThat(result.suspectedComponent()).isEqualTo("account-service");
        assertThat(result.llmAvailable()).isTrue();
    }

    @Test
    void degrades_when_trace_store_is_down() {
        Alert alert = new Alert("high-error-rate", "critical", null, null, TRACE_ID, Map.of(), Map.of());

        when(traceRepository.fetch(TRACE_ID)).thenThrow(new RuntimeException("trace store down"));
        when(llmGateway.complete(any())).thenReturn(Optional.of(new TriageAnswer("h", "account-service", "md")));

        TriageResult result = useCase.triage(alert);

        assertThat(result.llmAvailable()).isTrue();
        assertThat(result.suspectedComponent()).isEqualTo("account-service");
    }
}