package com.finpay.observability.aiops.infrastructure;

import com.finpay.observability.aiops.domain.TriagePromptAssembler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link AiOpsProperties} and wires the AI Ops triage module's domain
 * and infrastructure components (config-backed alert specs, trace store client
 * and LLM gateway are {@code @Component}s discovered by component scan; the
 * domain prompt assembler is intentionally a plain class and is registered
 * here so {@code domain/} stays free of Spring imports, Rule 4).
 */
@Configuration
@EnableConfigurationProperties(AiOpsProperties.class)
public class AiOpsConfig {

    @Bean
    public TriagePromptAssembler triagePromptAssembler() {
        return new TriagePromptAssembler();
    }
}