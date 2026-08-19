package com.finpay.observability.tracesummary.interfaces.web;

import com.finpay.observability.tracesummary.application.TraceSummarizationUseCase;
import com.finpay.observability.tracesummary.domain.TraceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes AI-assisted trace summarization: {@code GET /trace/{traceId}/summary}
 * returns a concise human-readable narrative of what happened in the trace.
 * Pure transport ↔ use case mapping (Rule 3).
 *
 * <p>Only the traceId is logged — never span payloads (PII policy).
 */
@RestController
@RequestMapping("/trace")
public class TraceSummaryController {

    private static final Logger log = LoggerFactory.getLogger(TraceSummaryController.class);

    private final TraceSummarizationUseCase summarizeUseCase;

    public TraceSummaryController(TraceSummarizationUseCase summarizeUseCase) {
        this.summarizeUseCase = summarizeUseCase;
    }

    @GetMapping(path = "/{traceId}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TraceSummary> summary(@PathVariable String traceId) {
        log.info("Trace summary requested: traceId={}", traceId);
        return ResponseEntity.ok(summarizeUseCase.summarize(traceId));
    }
}