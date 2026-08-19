package com.finpay.observability.tracesummary;

import com.finpay.observability.tracesummary.domain.TraceData;
import com.finpay.observability.tracesummary.domain.TraceRepository;
import com.finpay.observability.tracesummary.domain.TraceSpan;
import com.finpay.observability.tracesummary.domain.TraceSummaryLlmGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full Spring context (config binding, HttpClient clients, controller
 * wiring) and exercises the trace summary endpoint end-to-end with mocked LLM +
 * trace store so no external network is touched. MockMvc is built manually from
 * the context (Boot 4 removed auto MockMvc from {@code @SpringBootTest}).
 */
@SpringBootTest
class TraceSummaryContextTest {

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private TraceRepository traceRepository;

    @MockitoBean
    private TraceSummaryLlmGateway llmGateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void returns_llm_narrative_with_slowest_and_error_span_facts() throws Exception {
        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.of(new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "ERROR", 1250,
                        Map.of("http.status_code", "500")),
                new TraceSpan("2222222222222222", "transfer-service", "transfer.execute", "OK", 340, Map.of())))));
        when(llmGateway.complete(any())).thenReturn(Optional.of(
                "**Summary**: transfer failed.\n**Slowest span**: account-service POST /api/v1/transfers (1250 ms)."));

        mockMvc.perform(get("/trace/{traceId}/summary", TRACE_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.llmAvailable").value(true))
                .andExpect(jsonPath("$.narrative").value(containsString("transfer failed")))
                .andExpect(jsonPath("$.spanCount").value(2))
                .andExpect(jsonPath("$.slowestSpan.serviceName").value("account-service"))
                .andExpect(jsonPath("$.slowestSpan.durationMs").value(1250))
                .andExpect(jsonPath("$.errorSpans[0].operationName").value("POST /api/v1/transfers"));
    }

    @Test
    void falls_back_to_facts_only_summary_when_llm_unavailable() throws Exception {
        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.of(new TraceData(TRACE_ID, List.of(
                new TraceSpan("1111111111111111", "account-service", "POST /api/v1/transfers", "OK", 200, Map.of())))));
        when(llmGateway.complete(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/trace/{traceId}/summary", TRACE_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmAvailable").value(false))
                .andExpect(jsonPath("$.narrative").value(containsString("AI summary unavailable")))
                .andExpect(jsonPath("$.slowestSpan.durationMs").value(200));
    }

    @Test
    void returns_404_when_trace_is_unknown() throws Exception {
        when(traceRepository.fetch(TRACE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/trace/{traceId}/summary", TRACE_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACE_NOT_FOUND"));
    }
}