package com.finpay.observability.aiops.interfaces.web;

import com.finpay.observability.aiops.application.IncidentTriageUseCase;
import com.finpay.observability.aiops.domain.Alert;
import com.finpay.observability.aiops.domain.TriageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives alert webhooks (Grafana contact point "webhook") and triggers
 * AI-assisted incident triage. Pure transport ↔ use case mapping (Rule 3).
 *
 * <p>The endpoint always answers 200 with a {@link TriageResult} so the
 * alerting system never retries an observability sidecar.
 */
@RestController
@RequestMapping("/api/v1/aiops")
public class AiOpsTriageController {

    private static final Logger log = LoggerFactory.getLogger(AiOpsTriageController.class);

    private final IncidentTriageUseCase triageUseCase;

    public AiOpsTriageController(IncidentTriageUseCase triageUseCase) {
        this.triageUseCase = triageUseCase;
    }

    @PostMapping(path = "/triage", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TriageResult> triage(@RequestBody AiOpsAlertWebhookRequest request) {
        Alert alert = toAlert(request);
        log.info("AI triage triggered: alertName={}, severity={}, traceId={}",
                alert.alertName(), alert.severity(), alert.traceId());
        return ResponseEntity.ok(triageUseCase.triage(alert));
    }

    private Alert toAlert(AiOpsAlertWebhookRequest request) {
        Map<String, String> labels = request.labels() == null ? Map.of() : request.labels();
        Map<String, String> annotations = request.annotations() == null ? Map.of() : request.annotations();

        String alertName = firstNonBlank(
                request.alertName(),
                labels.get("alertname"),
                labels.get("alert_name"),
                request.title(),
                "unknown");
        String severity = firstNonBlank(request.severity(), labels.get("severity"), "unknown");
        String summary = firstNonBlank(
                annotations.get("summary"),
                annotations.get("description"),
                request.title(),
                "");
        String description = firstNonBlank(
                annotations.get("description"),
                annotations.get("message"),
                request.message(),
                "");
        String traceId = firstNonBlank(
                annotations.get("traceId"),
                annotations.get("trace_id"),
                labels.get("traceId"),
                labels.get("trace_id"),
                null);

        return new Alert(alertName, severity, summary, description, traceId, labels, annotations);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}