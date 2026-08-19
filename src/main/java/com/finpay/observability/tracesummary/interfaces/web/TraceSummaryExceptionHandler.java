package com.finpay.observability.tracesummary.interfaces.web;

import com.finpay.common.web.error.ProblemDetail;
import com.finpay.observability.tracesummary.domain.TraceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps domain exceptions to RFC-9457 problem responses for the trace
 * summarization endpoints. Keeps framework concerns out of {@code domain/}
 * (Rule 4).
 */
@RestControllerAdvice
public class TraceSummaryExceptionHandler {

    @ExceptionHandler(TraceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTraceNotFound(TraceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ProblemDetail(404, "TRACE_NOT_FOUND", e.getMessage(), null, Map.of()));
    }
}