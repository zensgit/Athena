# Verification: E2E List Polling Helper (2026-01-08)

## Command
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "UI smoke: browse" --project=chromium`

## Result
- PASS (1 test).

## Artifacts
- HTML report: `ecm-frontend/playwright-report/`
- Last run marker: `ecm-frontend/test-results/.last-run.json`
