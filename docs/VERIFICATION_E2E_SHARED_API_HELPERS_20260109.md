# Verification: E2E Shared API Helpers (2026-01-09)

## Command
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-sort-pagination.spec.ts --project=chromium`
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "UI smoke: browse" --project=chromium`
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`

## Result
- PASS (1 test each targeted run + 17/17 full suite).

## Artifacts
- HTML report: `ecm-frontend/playwright-report/`
- Last run marker: `ecm-frontend/test-results/.last-run.json`
