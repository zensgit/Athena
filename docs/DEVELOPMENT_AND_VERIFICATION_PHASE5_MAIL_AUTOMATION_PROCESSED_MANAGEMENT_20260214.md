# Phase 5 - Mail Automation Processed Management (Mocked-First E2E)

Date: 2026-02-14

## Goal

Add mocked-first Playwright coverage for the **Processed Messages** management actions in Mail Automation:

- Processed retention status (days + expired count)
- Retention cleanup (delete expired processed records)
- Bulk delete selected processed records
- Replay failed processed item
- View ingested documents for a processed message

This keeps operator UX regression coverage high even when Docker/backends are unavailable.

## Implementation

Changes:

- Added a new mocked E2E spec:
  - `ecm-frontend/e2e/mail-automation-processed-management.mock.spec.ts`
  - Uses **stateful route mocks** so post-action refreshes return updated retention/diagnostics data.
  - Accepts `window.confirm()` dialogs via Playwright `page.on('dialog', ...)` to exercise destructive flows deterministically.

- Included the spec in the Phase 5 mocked regression gate:
  - `scripts/phase5-regression.sh`

Mocked endpoints covered in the spec:

- `GET /api/v1/integration/mail/processed/retention`
- `POST /api/v1/integration/mail/processed/cleanup`
- `POST /api/v1/integration/mail/processed/bulk-delete`
- `POST /api/v1/integration/mail/processed/{id}/replay`
- `GET /api/v1/integration/mail/processed/{id}/documents`
- Plus page load prerequisites:
  - `GET /api/v1/integration/mail/accounts`
  - `GET /api/v1/integration/mail/rules`
  - `GET /api/v1/integration/mail/diagnostics`
  - `GET /api/v1/integration/mail/report`
  - `GET /api/v1/integration/mail/report/schedule`
  - `GET /api/v1/integration/mail/runtime-metrics`

## Verification (Playwright CLI)

Run the Phase 5 mocked regression gate:

```bash
bash scripts/phase5-regression.sh
```

Expected result:

- Gate ends with `phase5_regression: ok`
- Playwright reports `8 passed` (as of 2026-02-14, after adding this spec)

## Notes

- This spec does **not** verify real mail ingestion, only the admin/operator UI flows.
- Full-stack verification remains separate (requires Keycloak/DB/Elasticsearch + mail integration runtime).

