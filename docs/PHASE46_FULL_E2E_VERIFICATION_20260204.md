# PHASE46_FULL_E2E_VERIFICATION_20260204

## Command
- `cd ecm-frontend && npx playwright test`

## Result (2026-02-04)
- ❌ 32 failed / 34 total (2 passed)
- Primary failure pattern: login timeout waiting for `/browse/` after Keycloak redirect.
  - Example: `page.waitForURL(browsePattern, { timeout: 60000 })` in `e2e/*` login helpers.
- Secondary failures: feature dependencies not configured in this environment (mail automation, MFA, antivirus, rules data).

## Notes
- Full E2E was run without `ECM_E2E_SKIP_LOGIN=1`, so tests relied on interactive Keycloak flow.
- Recommend rerun with:
  - `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Ensure mail accounts / MFA / antivirus test fixtures are configured before running those suites.
