package com.finpay.observability.aiops.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port to the LLM provider. Implementations own the HTTP transport,
 * authentication (BYOK API key) and timeout/retry policy; the domain only
 * defines the contract. Returning {@link Optional#empty()} signals that no
 * answer could be produced (no key configured, provider down, timeout, ...).
 */
public interface LlmGateway {

    Optional<TriageAnswer> complete(List<ChatMessage> messages);
}
