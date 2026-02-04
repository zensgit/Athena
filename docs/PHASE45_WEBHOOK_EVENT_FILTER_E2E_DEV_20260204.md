# Phase 45 - Webhook Event Type Filter E2E (Development) - 2026-02-04

## Scope
- Add Playwright coverage to ensure webhook subscriptions honor selected event types.
- Validate Node-created notifications only reach subscriptions configured for `NODE_CREATED`.

## Changes
- `ecm-frontend/e2e/webhook-admin.spec.ts`
  - New test: **Webhook subscriptions honor event type filters**
  - Uses two local HTTP receivers (ports) with distinct secrets.
  - Creates two subscriptions via UI:
    - Sub A: `NODE_CREATED`
    - Sub B: `VERSION_CREATED`
  - Creates a folder via API to trigger `NODE_CREATED`.
  - Asserts:
    - Sub A receives `NODE_CREATED` with valid HMAC signature.
    - Sub B receives no `NODE_CREATED` events.

## Notes
- Uses `host.docker.internal` for callback URL so containerized backend can reach the local test servers.
- Cleans up created folder and subscriptions after assertions.
