package com.finpay.observability.tracesummary.infrastructure;

import com.finpay.observability.tracesummary.domain.TraceSummaryPromptAssembler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link TraceSummaryProperties} and wires the trace summarization
 * module's domain and infrastructure components ({@code HttpTraceRepository}
 * and {@code HttpTraceSummaryLlmGateway} are {@code @Component}s discovered by
 * component scan; the domain prompt assembler is intentionally a plain class
 * and is registered here so {@code domain/} stays free of Spring imports,
 * Rule 4).
 */
@Configuration
@EnableConfigurationProperties(TraceSummaryProperties.class)
public class TraceSummaryConfig {

    @Bean
    public TraceSummaryPromptAssembler traceSummaryPromptAssembler() {
        return new TraceSummaryPromptAssembler();
    }
}