# Phase 44 - MFA + Webhook E2E Development (2026-02-04)

## Scope
- Add Playwright E2E coverage for Local MFA settings and Webhook admin workflows.
- Ensure frontend container serves latest UI (Local MFA + Webhooks) for E2E validation.

## Changes
- Added webhook admin E2E test: `ecm-frontend/e2e/webhook-admin.spec.ts`.
  - Creates a local HTTP server, registers a webhook subscription, triggers a test event, and validates:
    - `X-ECM-Event: TEST`
    - `X-ECM-Signature` matches HMAC-SHA256 base64 over request body.
  - Deletes the subscription after validation.
  - Uses `host.docker.internal` to allow the containerized backend to reach the local test server.
- Maintained MFA E2E test: `ecm-frontend/e2e/mfa-settings.spec.ts`.
  - Uses API enrollment/verify/disable and validates Settings UI reflects Local MFA state.
- Cleanup: removed unused `useMemo` import from `ecm-frontend/src/pages/WebhookSubscriptionsPage.tsx`.

## Build Updates
Frontend container uses `Dockerfile.prebuilt` via `docker-compose.override.yml`. To ensure the running UI includes new pages:
- Built React static assets:
  - `npm ci`
  - `npm run build`
- Rebuilt frontend container:
  - `docker compose up -d --build ecm-frontend`

## Notes
- The E2E tests rely on `ECM_E2E_SKIP_LOGIN=1` for local token injection.
- Webhook signature verification matches backend implementation in `WebhookNotificationService`.
