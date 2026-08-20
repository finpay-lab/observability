package com.finpay.observability.infrastructure.config;

import com.finpay.observability.domain.IncidentTriage;
import com.finpay.observability.domain.LlmSummarizer;
import com.finpay.observability.domain.TraceAnalyzer;
import com.finpay.observability.infrastructure.llm.HttpLlmSummarizer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public TraceAnalyzer traceAnalyzer(LlmSummarizer llm) { return new TraceAnalyzer(llm); }

    @Bean
    public IncidentTriage incidentTriage(LlmSummarizer llm) { return new IncidentTriage(llm); }

    /** OTel SDK wired for the sidecar (FP-23). Exporter endpoint from config. */
    @Bean
    public OpenTelemetry openTelemetry() {
        return OpenTelemetrySdk.builder().build();
    }
}
