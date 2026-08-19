package com.finpay.observability.aiops.domain;

/**
 * One message of an LLM chat completion request ({@code system} / {@code user}
 * / {@code assistant}). Kept transport-agnostic so the gateway implementation
 * can map it to any provider's JSON contract.
 */
public record ChatMessage(String role, String content) {

    public ChatMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }
}
