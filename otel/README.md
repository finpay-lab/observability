# OTel across the SAGA (correlation ids)

How FinPay makes a single saga observable end-to-end (see `docs/OBSERVABILITY.md`
in the platform repo).

## Propagation contract

Every FinPay service runs the **OTel Java agent** with W3C trace context:

```sh
export OTEL_SERVICE_NAME=<service>
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318   # -> otel-collector
export OTEL_PROPAGATORS=tracecontext,baggage
export OTEL_METRICS_EXPORTER=prometheus                    # scrape /actuator/prometheus
export OTEL_TRACES_SAMPLER=parentbased_always_on           # lab: keep everything
```

Three propagation paths keep one `traceId` alive across the whole saga:

1. **Inbound HTTP** — OTel injects `traceparent`/`tracestate`; appenders put
   `traceId`/`spanId` on structured logs (common-observability `TraceContext`).
2. **Kafka produce/consume** — the producer puts `traceparent` in the message
   headers; the consumer resumes the parent span. This is what joins the SAGA
   hops (customer -> transfer -> payment -> ledger) in Tempo.
3. **Outbound REST/gRPC** — propagated automatically by the agent.

The `correlationId` (idempotency key / saga id) is carried as baggage so each
span is tagged with the saga it belongs to; Prometheus alerts and Tempo spans
can then be joined by `correlationId`.

## Collector

`otel-collector.yaml` receives OTLP on :4317 (gRPC) / :4318 (HTTP), batches,
adds `deployment.environment`, and exports traces to Tempo (`tempo:4317`) plus a
debug sink. Its own metrics are scraped by Prometheus on :8889.

## Trace backend

`docker-compose.yml` runs **Tempo** and provisions it as a Grafana datasource,
so any trace found on a Prometheus alert can be opened straight in Grafana.