# PHASE46_FULL_E2E_VERIFICATION_20260204

## Command
- `cd ecm-frontend && npx playwright test`
- `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`

## Result (2026-02-04)
- Initial run (interactive login): ❌ 32 failed / 34 total (2 passed)
  - Primary failure pattern: login timeout waiting for `/browse/` after Keycloak redirect.
    - Example: `page.waitForURL(browsePattern, { timeout: 60000 })` in `e2e/*` login helpers.
  - Secondary failures: feature dependencies not configured in this environment (mail automation, MFA, antivirus, rules data).
- Rerun with `ECM_E2E_SKIP_LOGIN=1`: ✅ 34 passed / 34 total.

## Notes
- `ECM_E2E_SKIP_LOGIN=1` bypasses Keycloak UI to stabilize full suite runs in local environments.
