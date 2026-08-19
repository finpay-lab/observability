package com.finpay.observability.tracesummary.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port to the LLM provider used for trace summarization. Implementations
 * own the HTTP transport, authentication (BYOK API key) and timeout/retry policy;
 * the domain only defines the contract. Returning {@link Optional#empty()}
 * signals that no narrative could be produced (no key configured, provider
 * down, timeout, malformed response, ...) and the caller falls back to a
 * deterministic facts-only summary.
 */
public interface TraceSummaryLlmGateway {

    Optional<String> complete(List<ChatMessage> messages);
}