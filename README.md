# observability

FinPay's observability package (docs/OBSERVABILITY.md in the platform repo).
Owns the three pillars as code:

- **Metrics & alerts** — `prometheus/prometheus.yml` (scrape config +
  `file_sd` targets in `prometheus/targets/`) and `prometheus/alert-rules.yml`
  (SAGA failures, outbox backlog, Kafka consumer lag, idempotency conflicts,
  circuit breakers, error-budget burn). Validated with `promtool`.
- **Traces** — `otel/otel-collector.yaml` (OTLP receiver, W3C traceparent
  across the SAGA, Tempo export) + `otel/tempo.yaml` trace backend. See
  `otel/README.md`.
- **Dashboards** — `grafana/dashboards/*.json` (service health, SAGA &
  payments, Kafka & infrastructure) provisioned through
  `grafana/provisioning/`.

`docker-compose.yml` runs the whole stack (Prometheus, Grafana, OTel collector,
Tempo). Business services run in their own repos and export
`/actuator/prometheus`; register their targets in `prometheus/targets/`.

Consumes shared libraries from `finpay-lab/platform` via composite build
(`includeBuild finpay-platform`, ADR-0011): this service exposes
`/actuator/prometheus` through `com.finpay:common-observability`.

Part of the [finpay-lab](https://github.com/finpay-lab) multi-repository
distributed-systems laboratory.