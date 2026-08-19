package com.finpay.observability.aiops.interfaces.web;

import com.finpay.observability.aiops.application.IncidentTriageUseCase;
import com.finpay.observability.aiops.domain.Alert;
import com.finpay.observability.aiops.domain.TriageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOpsTriageControllerTest {

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Test
    void maps_grafana_webhook_to_domain_alert_and_returns_result() {
        IncidentTriageUseCase useCase = mock(IncidentTriageUseCase.class);
        AiOpsTriageController controller = new AiOpsTriageController(useCase);
        TriageResult expected = new TriageResult(UUID.randomUUID(), TRACE_ID, "account-db",
                "https://runbooks.finpay.dev/high-error-rate.md", "## Summary", true);
        when(useCase.triage(any())).thenReturn(expected);

        AiOpsAlertWebhookRequest request = new AiOpsAlertWebhookRequest(
                "High error rate",
                "firing",
                "HTTP 500s above threshold",
                null,
                null,
                Map.of("alertname", "high-error-rate", "severity", "critical", "service", "account-service"),
                Map.of("summary", "5xx spike", "traceId", TRACE_ID));

        ResponseEntity<TriageResult> response = controller.triage(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(useCase).triage(captor.capture());
        Alert alert = captor.getValue();
        assertThat(alert.alertName()).isEqualTo("high-error-rate");
        assertThat(alert.severity()).isEqualTo("critical");
        assertThat(alert.summary()).isEqualTo("5xx spike");
        assertThat(alert.description()).isEqualTo("HTTP 500s above threshold");
        assertThat(alert.traceId()).isEqualTo(TRACE_ID);
        assertThat(alert.labels().get("service")).isEqualTo("account-service");
    }

    @Test
    void falls_back_to_title_when_alertname_label_is_missing() {
        IncidentTriageUseCase useCase = mock(IncidentTriageUseCase.class);
        AiOpsTriageController controller = new AiOpsTriageController(useCase);

        AiOpsAlertWebhookRequest request = new AiOpsAlertWebhookRequest(
                "Payment timeout alert", "firing", null, null, null, Map.of(), Map.of());

        controller.triage(request);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(useCase).triage(captor.capture());
        assertThat(captor.getValue().alertName()).isEqualTo("Payment timeout alert");
    }

    @Test
    void ignores_trace_id_that_is_not_a_32_hex_w3c_trace_id() {
        IncidentTriageUseCase useCase = mock(IncidentTriageUseCase.class);
        AiOpsTriageController controller = new AiOpsTriageController(useCase);

        AiOpsAlertWebhookRequest request = new AiOpsAlertWebhookRequest(
                null, "firing", null, "high-error-rate", null, Map.of(), Map.of("traceId", "../../etc/passwd"));

        controller.triage(request);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(useCase).triage(captor.capture());
        assertThat(captor.getValue().traceId()).isNull();
    }
}