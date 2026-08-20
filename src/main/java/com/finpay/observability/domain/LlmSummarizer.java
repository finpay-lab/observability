package com.finpay.observability.domain;

/** LLM port for observability narratives (FP-61/62). BYOK; never logs PII. */
public interface LlmSummarizer {
    String summarize(String prompt);
}
