package com.finpay.observability.tracesummary.interfaces.web;

import com.finpay.common.web.error.ProblemDetail;
import com.finpay.observability.tracesummary.application.TraceSummarizationUseCase;
import com.finpay.observability.tracesummary.domain.TraceNotFoundException;
import com.finpay.observability.tracesummary.domain.TraceSpanInfo;
import com.finpay.observability.tracesummary.domain.TraceSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceSummaryControllerTest {

    private static final String TRACE_ID = "abc123abc123abc123abc123abc123ab";

    @Test
    void returns_summary_for_trace() {
        TraceSummarizationUseCase useCase = mock(TraceSummarizationUseCase.class);
        TraceSummaryController controller = new TraceSummaryController(useCase);
        TraceSummary expected = new TraceSummary(
                TRACE_ID,
                "**Summary**: transfer failed.",
                new TraceSpanInfo("1111111111111111", "account-service", "POST /api/v1/transfers", "ERROR", 1250),
                List.of(new TraceSpanInfo("1111111111111111", "account-service", "POST /api/v1/transfers", "ERROR", 1250)),
                2,
                true);
        when(useCase.summarize(TRACE_ID)).thenReturn(expected);

        ResponseEntity<TraceSummary> response = controller.summary(TRACE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(useCase).summarize(TRACE_ID);
    }

    @Test
    void maps_trace_not_found_to_404_problem_detail() {
        TraceSummaryExceptionHandler handler = new TraceSummaryExceptionHandler();

        ResponseEntity<ProblemDetail> response =
                handler.handleTraceNotFound(new TraceNotFoundException(TRACE_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("TRACE_NOT_FOUND");
        assertThat(response.getBody().message()).contains(TRACE_ID);
    }
}