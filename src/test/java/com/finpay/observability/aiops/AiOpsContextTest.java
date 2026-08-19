package com.finpay.observability.aiops;

import com.finpay.observability.aiops.domain.LlmGateway;
import com.finpay.observability.aiops.domain.TriageAnswer;
import com.finpay.observability.aiops.domain.TraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full Spring context (config binding, HttpClient clients, controller
 * wiring) and exercises the alert webhook end-to-end with mocked LLM + trace
 * store so no external network is touched. MockMvc is built manually from the
 * context (Boot 4 removed auto MockMvc from {@code @SpringBootTest}).
 */
@SpringBootTest
class AiOpsContextTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private LlmGateway llmGateway;

    @MockitoBean
    private TraceRepository traceRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void alert_webhook_triggers_triage_and_returns_markdown_result() throws Exception {
        when(llmGateway.complete(any())).thenReturn(Optional.of(new TriageAnswer(
                "Connection pool exhausted",
                "account-db",
                "## Root cause\nConnection pool exhaustion in account-db.")));

        mockMvc.perform(post("/api/v1/aiops/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "High error rate",
                                  "state": "firing",
                                  "message": "HTTP 500s above threshold",
                                  "labels": {
                                    "alertname": "high-error-rate",
                                    "severity": "critical",
                                    "service": "account-service"
                                  },
                                  "annotations": {
                                    "summary": "5xx spike",
                                    "traceId": "abc123abc123abc123abc123abc123ab"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmAvailable").value(true))
                .andExpect(jsonPath("$.suspectedComponent").value("account-db"))
                .andExpect(jsonPath("$.traceId").value("abc123abc123abc123abc123abc123ab"))
                .andExpect(jsonPath("$.markdownSummary").value(containsString("account-db")));
    }

    @Test
    void webhook_returns_context_fallback_when_llm_unavailable() throws Exception {
        when(llmGateway.complete(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/aiops/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "labels": {
                                    "alertname": "high-error-rate",
                                    "severity": "warning"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmAvailable").value(false))
                .andExpect(jsonPath("$.markdownSummary").value(containsString("AI Triage unavailable")));
    }
}