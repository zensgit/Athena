# Athena ECM Rollup Verification (2026-02-05)

## Automated
- Backend unit/integration
  - `cd ecm-core && mvn test`
  - Result: **BUILD SUCCESS** (Tests run: 138, Failures: 0, Errors: 0)
- Frontend E2E (Playwright, full suite)
  - `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Result: **36 passed** (4.7m)
- Targeted E2E (permission templates + preview status)
  - `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/permission-templates.spec.ts e2e/search-preview-status.spec.ts`
  - Result: **4 passed**

## Manual
- None (all coverage executed via Playwright for this rollup).

## Notes
- Playwright logs show expected mail automation diagnostics and scheduled rule checks.
- CSV export validated via download assertion in `e2e/permission-templates.spec.ts`.
