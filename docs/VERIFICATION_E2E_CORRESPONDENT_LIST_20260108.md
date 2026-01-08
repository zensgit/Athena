# Verification: E2E Correspondent List Stability (2026-01-08)

## Scope
- UI smoke correspondent create + search row visibility after list refresh.

## Command
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "UI smoke: browse" --project=chromium`

## Result
- PASS (1 test).

## Artifacts
- HTML report: `ecm-frontend/playwright-report/`
- Last run marker: `ecm-frontend/test-results/.last-run.json`

## Full suite follow-up
- Command: `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test --project=chromium`
- Result: PASS (17 tests).
- HTML report: `ecm-frontend/playwright-report/`
