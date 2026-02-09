# Phase 1 P65 - Weekly Regression + Release Gate (Verification) - 2026-02-09

## What This Verifies

This verifies that the current branch remains stable via:

- API smoke checks (token-authenticated)
- Focused Playwright E2E checks for newly added/critical flows

For the full weekly runbook (including WOPI and optional rebuild), see:

- `docs/VERIFICATION_WEEKLY_REGRESSION_RUNBOOK_20260209.md`

## Fast Gate (Recommended)

### 1) API Smoke

```bash
./scripts/get-token.sh admin admin
ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token bash scripts/smoke.sh
```

Expected:
- Smoke script completes without failures.

### 2) Focused E2E

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts --grep "processed item can show ingested documents dialog"

ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/permission-explanation.spec.ts
```

Expected:
- Mail test: `1 passed` (or skipped only if there are no processed messages)
- Permission test: `1 passed`

## Results

Executed:

- API smoke:
  - `ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token bash scripts/smoke.sh`
  - Result: **PASSED** (warnings observed but script completed successfully)
- Playwright:
  - `e2e/mail-automation.spec.ts --grep "processed item can show ingested documents dialog"`: **PASSED** (`1 passed`)
  - `e2e/permission-explanation.spec.ts`: **PASSED** (`1 passed`)
