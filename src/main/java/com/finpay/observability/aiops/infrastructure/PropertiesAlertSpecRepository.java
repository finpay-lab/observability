package com.finpay.observability.aiops.infrastructure;

import com.finpay.observability.aiops.domain.AlertSpec;
import com.finpay.observability.aiops.domain.AlertSpecRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Config-backed {@link AlertSpecRepository}: the known alert rules (PromQL
 * query + runbook URL) are declared under {@code finpay.aiops.alert-specs}.
 */
@Component
public class PropertiesAlertSpecRepository implements AlertSpecRepository {

    private final Map<String, AlertSpec> byName;

    public PropertiesAlertSpecRepository(AiOpsProperties properties) {
        Map<String, AlertSpec> specs = new HashMap<>();
        for (AiOpsProperties.AlertSpecConfig config : properties.alertSpecs()) {
            specs.put(config.alertName(), new AlertSpec(config.alertName(), config.promql(), config.runbookUrl()));
        }
        this.byName = Map.copyOf(specs);
    }

    @Override
    public Optional<AlertSpec> findByAlertName(String alertName) {
        return Optional.ofNullable(byName.get(alertName));
    }
}