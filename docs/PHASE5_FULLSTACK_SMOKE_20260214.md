# Phase 5 - Full-Stack Smoke (Optional, Docker Required)

Date: 2026-02-14

## Goal

Provide a single CLI entrypoint to validate that key **Phase 5 admin pages** render correctly against the real stack:

- Mail Automation (`/admin/mail`)
- Preview Diagnostics (`/admin/preview-diagnostics`)

This is intentionally small and fast compared to broader suites like `ui-smoke.spec.ts`.

## Prerequisites

- Backend API is healthy:
  - `curl -fsS http://localhost:7700/actuator/health`
- Keycloak is reachable:
  - `curl -fsS http://localhost:8180/realms/ecm/.well-known/openid-configuration`
- Frontend dev server is running (recommended):
  - `http://localhost:3000`

Note:
- The static server on `:5500` does **not** proxy `/api` calls, so it is not suitable for full-stack UI tests unless you are behind a reverse-proxy that forwards `/api` to `:7700`.

## What It Runs

- Script: `scripts/phase5-fullstack-smoke.sh`
- Playwright spec: `ecm-frontend/e2e/phase5-fullstack-admin-smoke.spec.ts`

Auth behavior:
- The E2E helper attempts to obtain an access token from Keycloak and seeds the UI session via `localStorage`, avoiding flaky UI login steps.

## Command

```bash
bash scripts/phase5-fullstack-smoke.sh
```

## Environment Overrides

- `ECM_UI_URL` (default `http://localhost:3000`)
- `ECM_API_URL` (default `http://localhost:7700`)
- `KEYCLOAK_URL` (default `http://localhost:8180`)
- `KEYCLOAK_REALM` (default `ecm`)
- `ECM_E2E_USERNAME` / `ECM_E2E_PASSWORD` (default `admin` / `admin`)
- `PW_PROJECT` (default `chromium`)
- `PW_WORKERS` (default `1`)

Example:

```bash
ECM_UI_URL=http://localhost:3000 ECM_E2E_USERNAME=admin ECM_E2E_PASSWORD=admin \
  bash scripts/phase5-fullstack-smoke.sh
```

## Expected Result

- Playwright reports `1 passed`
- Script ends with `phase5_fullstack_smoke: ok`

## 2026-02-15 Post-Merge Run (PASS)

Environment:
- `ecm-core`, `ecm-frontend`, and `nginx` containers running.
- `ECM_UI_URL=http://localhost` (nginx reverse-proxy target that forwards `/api` to backend).

Command:

```bash
ECM_UI_URL=http://localhost bash scripts/phase5-fullstack-smoke.sh
```

Observed:
- `phase5_fullstack_smoke: run playwright (full-stack admin smoke)` executed successfully.
- Playwright output: `1 passed`.
- Script ended with `phase5_fullstack_smoke: ok`.
