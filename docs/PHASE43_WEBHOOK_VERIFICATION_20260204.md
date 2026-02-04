# Phase 43 - Webhook Verification (2026-02-04)

## Setup
- Local receiver started on host: `http://localhost:8098/webhook`
- Subscription URL set to `http://host.docker.internal:8098/webhook` (container -> host)
- Secret: `local-test-secret`
- Event types: `TEST`, `NODE_CREATED`

## Steps
1. Create subscription via `/api/v1/webhooks`.
2. Trigger test delivery via `/api/v1/webhooks/{id}/test`.
3. Receiver logged request to `/tmp/webhook_requests.jsonl`.

## Results
- Delivery received with headers:
  - `X-ECM-Event: TEST`
  - `X-ECM-Delivery: <uuid>`
  - `X-ECM-Timestamp: <epoch>`
  - `X-ECM-Signature: <base64>`
- Signature validated locally (HMAC SHA256 over body) — **match = true**.
- Subscription deleted after test (HTTP 204).

## Notes
- Localhost inside the container cannot reach host directly; `host.docker.internal` was required.
