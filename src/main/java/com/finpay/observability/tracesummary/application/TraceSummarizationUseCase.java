package com.finpay.observability.tracesummary.application;

import com.finpay.observability.tracesummary.domain.ChatMessage;
import com.finpay.observability.tracesummary.domain.TraceData;
import com.finpay.observability.tracesummary.domain.TraceFacts;
import com.finpay.observability.tracesummary.domain.TraceNotFoundException;
import com.finpay.observability.tracesummary.domain.TraceRepository;
import com.finpay.observability.tracesummary.domain.TraceSummary;
import com.finpay.observability.tracesummary.domain.TraceSummaryLlmGateway;
import com.finpay.observability.tracesummary.domain.TraceSummaryPromptAssembler;
import com.finpay.observability.tracesummary.domain.TraceSpanInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application use case (Rule 3: controllers delegate to use cases) that
 * orchestrates trace summarization:
 *
 * <ol>
 *   <li>pull the OTel trace referenced by the traceId from the trace store,</li>
 *   <li>compute the deterministic facts (slowest span, error spans),</li>
 *   <li>assemble the LLM prompt,</li>
 *   <li>ask the LLM for a concise narrative,</li>
 *   <li>map the result to a {@link TraceSummary}.</li>
 * </ol>
 *
 * <p>Degraded gracefully: a missing trace surfaces a {@link
 * TraceNotFoundException} (HTTP 404); an unavailable LLM never throws, the
 * caller gets a deterministic facts-only summary. This service is a pure
 * observability read path and has zero impact on money paths.
 */
@Service
public class TraceSummarizationUseCase {

    private final TraceRepository traceRepository;
    private final TraceSummaryLlmGateway llmGateway;
    private final TraceSummaryPromptAssembler promptAssembler;

    public TraceSummarizationUseCase(
            TraceRepository traceRepository,
            TraceSummaryLlmGateway llmGateway,
            TraceSummaryPromptAssembler promptAssembler
    ) {
        this.traceRepository = traceRepository;
        this.llmGateway = llmGateway;
        this.promptAssembler = promptAssembler;
    }

    public TraceSummary summarize(String traceId) {
        TraceData trace = traceRepository.fetch(traceId)
                .orElseThrow(() -> new TraceNotFoundException(traceId));
        TraceFacts facts = TraceFacts.compute(trace);
        List<ChatMessage> messages = promptAssembler.assemble(trace, facts);

        Optional<String> narrative = llmGateway.complete(messages);
        return narrative
                .map(n -> new TraceSummary(
                        traceId, n, facts.slowestSpan(), facts.errorSpans(), facts.spanCount(), true))
                .orElseGet(() -> new TraceSummary(
                        traceId,
                        fallbackNarrative(trace, facts),
                        facts.slowestSpan(),
                        facts.errorSpans(),
                        facts.spanCount(),
                        false));
    }

    /**
     * Deterministic fallback used when the LLM step could not run (no
     * {@code AI_LLM_API_KEY} configured, provider unreachable or timeout). The
     * endpoint still returns the key facts so debugging never dead-ends.
     */
    private String fallbackNarrative(TraceData trace, TraceFacts facts) {
        StringBuilder md = new StringBuilder();
        md.append("## AI summary unavailable\n\n");
        md.append("The LLM narrative could not be produced (no `AI_LLM_API_KEY` configured, LLM "
                + "endpoint unreachable, or timeout). Deterministic trace facts:\n\n");
        md.append("- traceId: ").append(trace.traceId()).append('\n');
        md.append("- spans: ").append(facts.spanCount()).append('\n');
        md.append("- slowest span: ").append(formatSpan(facts.slowestSpan())).append('\n');
        if (facts.errorSpans().isEmpty()) {
            md.append("- error spans: none\n");
        } else {
            md.append("- error spans: ").append(facts.errorSpans().size()).append('\n');
            for (var span : facts.errorSpans()) {
                md.append("  - ").append(formatSpan(span)).append('\n');
            }
        }
        return md.toString();
    }

    private String formatSpan(TraceSpanInfo span) {
        if (span == null) {
            return "none";
        }
        return span.serviceName() + " " + span.operationName()
                + " (" + span.durationMs() + " ms, " + span.status() + ")";
    }
}