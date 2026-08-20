package com.finpay.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Placeholder for the legacy flat package. Canonical app is com/finpay/observability/ObservabilityApplication. */
class ObservabilityApplicationTest {
    @Test
    void legacyBootstrapLoads() {
        assertThat(ObservabilityApplication.class).isNotNull();
    }
}
