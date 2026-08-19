package com.finpay.observability.aiops.application;

import com.finpay.observability.aiops.domain.Alert;
import com.finpay.observability.aiops.domain.AlertSpec;
import com.finpay.observability.aiops.domain.AlertSpecRepository;
import com.finpay.observability.aiops.domain.ChatMessage;
import com.finpay.observability.aiops.domain.LlmGateway;
import com.finpay.observability.aiops.domain.TraceData;
import com.finpay.observability.aiops.domain.TraceRepository;
import com.finpay.observability.aiops.domain.TriageAnswer;
import com.finpay.observability.aiops.domain.TriagePromptAssembler;
import com.finpay.observability.aiops.domain.TriageResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application use case (Rule 3: controllers delegate to use cases) that
 * orchestrates AI incident triage:
 *
 * <ol>
 *   <li>resolve the alert's spec (PromQL query + runbook),</li>
 *   <li>pull the OTel trace referenced by the alert payload,</li>
 *   <li>assemble the LLM prompt,</li>
 *   <li>ask the LLM for a root-cause hypothesis,</li>
 *   <li>map the answer to a {@link TriageResult}.</li>
 * </ol>
 *
 * <p>Everything is degraded gracefully: a missing spec, an unreachable trace
 * store or an unavailable LLM never throws; the caller always gets a
 * {@link TriageResult}. This service is a pure observability sidecar and must
 * have zero impact on money paths.
 */
@Service
public class IncidentTriageUseCase {

    private final AlertSpecRepository alertSpecRepository;
    private final TraceRepository traceRepository;
    private final LlmGateway llmGateway;
    private final TriagePromptAssembler promptAssembler;

    public IncidentTriageUseCase(
            AlertSpecRepository alertSpecRepository,
            TraceRepository traceRepository,
            LlmGateway llmGateway,
            TriagePromptAssembler promptAssembler
    ) {
        this.alertSpecRepository = alertSpecRepository;
        this.traceRepository = traceRepository;
        this.llmGateway = llmGateway;
        this.promptAssembler = promptAssembler;
    }

    public TriageResult triage(Alert alert) {
        Optional<AlertSpec> spec = alertSpecRepository.findByAlertName(alert.alertName());
        Optional<TraceData> trace = fetchTrace(alert);
        List<ChatMessage> messages = promptAssembler.assemble(alert, spec, trace);

        Optional<TriageAnswer> answer = llmGateway.complete(messages);
        return answer
                .map(a -> new TriageResult(
                        UUID.randomUUID(),
                        alert.traceId(),
                        a.suspectedComponent(),
                        spec.map(AlertSpec::runbookUrl).orElse(null),
                        a.summaryMarkdown(),
                        true))
                .orElseGet(() -> fallbackResult(alert, spec, trace));
    }

    private Optional<TraceData> fetchTrace(Alert alert) {
        if (alert.traceId() == null) {
            return Optional.empty();
        }
        try {
            return traceRepository.fetch(alert.traceId());
        } catch (RuntimeException e) {
            // The trace store may be down or the trace not yet indexed; the
            // triage must still proceed without it.
            return Optional.empty();
        }
    }

    private TriageResult fallbackResult(Alert alert, Optional<AlertSpec> spec, Optional<TraceData> trace) {
        StringBuilder md = new StringBuilder();
        md.append("## AI Triage unavailable\n\n");
        md.append("The LLM summarisation step could not be completed (no `AI_LLM_API_KEY` configured, "
                + "LLM endpoint unreachable, or timeout). Falling back to raw alert context.\n\n");
        md.append("### Alert\n");
        md.append("- alertname: ").append(alert.alertName()).append('\n');
        md.append("- severity: ").append(alert.severity()).append('\n');
        if (alert.summary() != null && !alert.summary().isBlank()) {
            md.append("- summary: ").append(alert.summary()).append('\n');
        }
        spec.ifPresent(s -> {
            md.append("- promql: `").append(s.promql()).append("`\n");
            md.append("- runbook: ").append(s.runbookUrl()).append('\n');
        });
        trace.ifPresent(t -> md.append("- trace spans available: ").append(t.spans().size()).append(" spans\n"));

        return new TriageResult(
                UUID.randomUUID(),
                alert.traceId(),
                null,
                spec.map(AlertSpec::runbookUrl).orElse(null),
                md.toString(),
                false);
    }
}
