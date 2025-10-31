# Observability: OpenTelemetry Tracing

## Goal
Distributed traces across frontend, backend, and async jobs.

## Plan
- Backend: spring-boot + OTel Java agent; export OTLP.
- Frontend: web tracer SDK for key flows.
- Context propagation through RabbitMQ headers.

## Dashboards
Latency percentiles, error rates, queue depth.

