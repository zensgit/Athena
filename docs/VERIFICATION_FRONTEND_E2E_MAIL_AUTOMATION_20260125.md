# Verification: Frontend E2E + Mail Automation (2026-01-25)

## Scope
- Mail Automation E2E coverage (new test + UI smoke integration).
- Full Playwright regression for frontend.

## Environment
- UI: http://localhost:3000
- API: http://localhost:7700
- Keycloak: http://localhost:8180

## Fix Applied (Prerequisite)
- Permission mismatch on `/var/ecm/content` caused upload failures (`Content storage failed`).
- Resolved by updating ownership inside `athena-ecm-core-1`:
  - `docker exec -u 0 athena-ecm-core-1 chown -R app:app /var/ecm/content`
  - Created missing date folder: `/var/ecm/content/2026/01/25`
- Hardening: `ecm-core` entrypoint now fixes `/var/ecm/content` ownership on startup.

## Test Runs

### Mail Automation E2E (dedicated)
- Command:
  - `ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/mail-automation.spec.ts --config playwright.config.ts`
- Result: PASS
- Coverage:
  - `/admin/mail` loads
  - “Test connection” toast
  - “Trigger Fetch” toast

### Full Playwright Regression
- Command:
  - `ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test`
- Result: PASS (21/21)
- Notes:
  - EICAR upload rejected as expected (AV check).

## Artifacts
- Playwright HTML report under `ecm-frontend/playwright-report` (if enabled).
- Failure artifacts were not generated in the successful run.
