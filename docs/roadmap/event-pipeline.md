# Event Pipeline (Retry + DLQ)

## Goal
Standardize async events with retries and DLQ.

## Design
- RabbitMQ exchanges/queues per bounded context.
- Spring Retry with exponential backoff; DLQ routing after N tries.
- Idempotency keys for handlers.

## Observability
Metrics + traces; DLQ dashboards.

## Test Plan
Contract tests for event schemas; chaos tests on broker restarts.

