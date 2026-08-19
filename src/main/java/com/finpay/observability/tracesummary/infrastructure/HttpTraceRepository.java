package com.finpay.observability.tracesummary.infrastructure;

import com.finpay.observability.tracesummary.domain.TraceData;
import com.finpay.observability.tracesummary.domain.TraceRepository;
import com.finpay.observability.tracesummary.domain.TraceSpan;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Fetches OTel traces from a Jaeger-compatible query API
 * ({@code GET {baseUrl}/api/traces/{traceId}}), e.g. the Jaeger UI backend
 * deployed next to the OTel collector. Distilled into a domain {@link TraceData}.
 *
 * <p>Remote-dependency policy (Rule 8): a connect timeout and a per-request
 * timeout. This is a best-effort read for an observability sidecar: any
 * failure (unreachable store, malformed payload, trace not yet indexed) yields
 * {@link Optional#empty()} and the caller decides how to surface it.
 *
 * <p>Only the traceId and a warning with the HTTP status are logged — never
 * span payloads (PII policy).
 */
@Component
public class HttpTraceRepository implements TraceRepository {

    private static final Logger log = LoggerFactory.getLogger(HttpTraceRepository.class);

    private static final String OTEL_STATUS_CODE = "otel.status_code";
    private static final String OTEL_STATUS_DESCRIPTION = "otel.status_description";
    private static final String ERROR_TAG = "error";

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;

    public HttpTraceRepository(TraceSummaryProperties properties, ObjectMapper objectMapper) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(properties.traceStore().baseUrl());
        this.timeout = properties.traceStore().timeout();
    }

    @Override
    public Optional<TraceData> fetch(String traceId) {
        String url = baseUrl + "/api/traces/" + traceId;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Trace store returned {} for traceId={}", response.statusCode(), traceId);
                return Optional.empty();
            }
            JaegerTracesResponse parsed =
                    objectMapper.readValue(response.body(), JaegerTracesResponse.class);
            return mapToDomain(parsed, traceId);
        } catch (IOException e) {
            log.warn("Failed to fetch trace {}: {}", traceId, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while fetching trace {}", traceId);
            return Optional.empty();
        }
    }

    private Optional<TraceData> mapToDomain(JaegerTracesResponse parsed, String traceId) {
        if (parsed.data() == null || parsed.data().isEmpty()) {
            return Optional.empty();
        }
        JaegerTrace trace = parsed.data().get(0);
        if (trace.spans() == null) {
            return Optional.empty();
        }
        List<TraceSpan> spans = new ArrayList<>();
        for (JaegerSpan span : trace.spans()) {
            String service = null;
            if (trace.processes() != null && span.processID() != null) {
                JaegerProcess process = trace.processes().get(span.processID());
                if (process != null) {
                    service = process.serviceName();
                }
            }
            Map<String, String> attributes = extractAttributes(span.tags());
            String status = attributes.getOrDefault(OTEL_STATUS_CODE, "UNSET");
            if (span.tags() != null) {
                for (JaegerTag tag : span.tags()) {
                    if (ERROR_TAG.equals(tag.key()) && Boolean.TRUE.equals(tag.value())) {
                        status = "ERROR";
                        break;
                    }
                }
            }
            spans.add(new TraceSpan(
                    span.spanID(),
                    service,
                    span.operationName(),
                    status,
                    TimeUnit.MICROSECONDS.toMillis(span.duration()),
                    attributes));
        }
        return Optional.of(new TraceData(traceId, spans));
    }

    private Map<String, String> extractAttributes(List<JaegerTag> tags) {
        Map<String, String> attributes = new HashMap<>();
        if (tags == null) {
            return attributes;
        }
        for (JaegerTag tag : tags) {
            String key = tag.key();
            if (OTEL_STATUS_DESCRIPTION.equals(key)) {
                continue;
            }
            Object value = tag.value();
            if (value != null) {
                attributes.put(key, String.valueOf(value));
            }
        }
        return attributes;
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // --- Jaeger query API DTOs (subset of the response we need) ---

    record JaegerTracesResponse(@JsonProperty("data") List<JaegerTrace> data) {
    }

    record JaegerTrace(
            @JsonProperty("traceID") String traceID,
            @JsonProperty("spans") List<JaegerSpan> spans,
            @JsonProperty("processes") Map<String, JaegerProcess> processes
    ) {
    }

    record JaegerSpan(
            @JsonProperty("spanID") String spanID,
            @JsonProperty("operationName") String operationName,
            @JsonProperty("startTime") long startTime,
            @JsonProperty("duration") long duration,
            @JsonProperty("processID") String processID,
            @JsonProperty("tags") List<JaegerTag> tags
    ) {
    }

    record JaegerTag(
            @JsonProperty("key") String key,
            @JsonProperty("value") Object value,
            @JsonProperty("type") String type
    ) {
    }

    record JaegerProcess(
            @JsonProperty("serviceName") String serviceName,
            @JsonProperty("tags") List<JaegerTag> tags
    ) {
    }
}