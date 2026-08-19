package com.finpay.observability.aiops.domain;

import java.util.Optional;

/**
 * Resolves the static specification of a known alert rule (PromQL query +
 * runbook link) from its alert name. Implementation lives in
 * {@code infrastructure/} (config-backed).
 */
public interface AlertSpecRepository {

    Optional<AlertSpec> findByAlertName(String alertName);
}
