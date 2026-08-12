## Summary

Implements the observability phase for FinPay: Prometheus alerting rules,
Grafana dashboards, and OpenTelemetry tracing across the SAGA (W3C traceparent
via correlation ids/Tempo) as code, plus a config linter wired into CI that
verifies everything renders and parses (per docs/OBSERVABILITY.md and P10/TASK-100).

## Changes

- `prometheus/prometheus.yml` — scrape config for the OTel collector (:8888/:8889), Grafana and FinPay business services via `file_sd` targets (`prometheus/targets/services.yml`, no restart on change); loads `alert-rules.yml`.
- `prometheus/alert-rules.yml` — 14 rules (validated by `promtool check rules`) covering service-down, 5xx/RED error budget, p95 SLO, payment failure ratio/volume drop, SAGA failures + compensations, idempotency conflicts, outbox backlog, open circuit breakers, Kafka consumer lag (+absent lag), DB pool exhaustion, Redis rate-limit rejections.
- `otel/otel-collector.yaml` — OTLP gRPC/HTTP receiver, memory limiter, resource + batch processors, debug + Tempo (traces) + Prometheus re-export (:8889) exporters; validated by booting the contrib collector.
- `otel/tempo.yaml` + `otel/README.md` — trace backend and the propagation contract (HTTP/Kafka traceparent, correlationId as baggage, agent env).
- `grafana/dashboards/*.json` — three importable dashboards (Service Health RED/SLO, SAGA & Payments, Kafka & Infrastructure) referencing only metrics from OBSERVABILITY.md.
- `grafana/provisioning/` — auto-provisions the Prometheus + Tempo datasources and mounts the dashboards.
- `docker-compose.yml` — one command stack: Prometheus, Grafana, OTel collector, Tempo.
- `.github/workflows/ci.yml` — new `observability-lint` job (dashboard structure/expr checks, YAML parse, `promtool check config` + `check rules`); existing build job still green.
- `src/main/resources/application.yml` + `build.gradle` — expose `/actuator/prometheus` and health probes via `com.finpay:common-observability` (composite build, ADR-0011).
- `scripts/lint-observability.py` — dashboard/expression/uid/datasource and YAML cross-file checks, shared by CI and locally.

## Testing

- `python3 scripts/lint-observability.py` → `OK: observability config valid`.
- `promtool check config` + `promtool check rules` (prom/prometheus:v2.54.1) → SUCCESS, 14 rules.
- Booted `otel/opentelemetry-collector-contrib:0.112.0` with the config → "Everything is ready".
- Booted `grafana/tempo:2.6.1` with `tempo.yaml` → listening on :3200/:4317/:4318. (Retention key corrected after a fail-fast parse error.)
- `docker compose config --quiet` → OK.
- `docker run gradle:8.10.2-jdk21 gradle clean build --no-daemon` → BUILD SUCCESSFUL (tests pass).

## Risks

- Alert/dashboard metric names are the OBSERVABILITY.md contract; if a service exports a metric under a different name later, its rules/dashboards go silent until aligned. No runtime wiring yet — the Java agent env (otel/README.md) is documented, not enforced.
- `file_sd` targets are empty except `observability` on localhost:8080; unregistered services simply produce no samples (no false alerts).
- `FinPayCircuitBreakerOpen` treats `circuit_breaker_open_total` as a gauge (noted in the rule); a future counter/gauge split should update the expression.