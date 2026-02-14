# Phase 5 - Mail Automation Diagnostics + Export (Mocked-First E2E)

Date: 2026-02-14

## Goal

Strengthen Phase 5 regression coverage for **Mail Automation** without requiring Docker/backends by adding mocked E2E checks for:

- `List Folders` (IMAP folder listing)
- `Run Diagnostics` (dry-run fetch diagnostics)
- `Export CSV` (Mail Reporting + Recent Mail Activity diagnostics)
- `Copy link` (diagnostics deep link clipboard copy)

## Implementation

Changes:

- Added a new mocked E2E spec:
  - `ecm-frontend/e2e/mail-automation-diagnostics-export.mock.spec.ts`
  - Stubs these endpoints:
    - `GET /api/v1/integration/mail/accounts`
    - `GET /api/v1/integration/mail/rules`
    - `GET /api/v1/integration/mail/accounts/{id}/folders`
    - `POST /api/v1/integration/mail/fetch/debug`
    - `GET /api/v1/integration/mail/report` + `GET /api/v1/integration/mail/report/export`
    - `GET /api/v1/integration/mail/diagnostics` + `GET /api/v1/integration/mail/diagnostics/export`
  - Uses an init-script hook to capture `a[download]` clicks to validate blob-based exports deterministically in CI.

- Included the new spec in the Phase 5 mocked regression gate:
  - `scripts/phase5-regression.sh`

- Updated docs to reflect the expanded regression gate:
  - `docs/PHASE5_REGRESSION_GATE_ROLLUP_20260214.md`
  - `docs/NEXT_7DAY_PLAN_PHASE5_20260213.md`

## Verification (Playwright CLI)

Run the Phase 5 regression gate:

```bash
bash scripts/phase5-regression.sh
```

Expected result:

- `7 passed`
- Gate ends with `phase5_regression: ok`

## Notes

- This suite is **mocked-first** and does not require Docker.
- The export flows in `MailAutomationPage.tsx` use `URL.createObjectURL` and `anchor.click()`. The E2E spec captures these clicks instead of relying on OS download integrations.

