# Verification: Mail Automation Fetch Diagnostics (Dry Run) (2026-01-27)

## Backend Tests
- Full backend suite:
  - Command: `cd ecm-core && mvn -q test`
  - Result: ✅ Passed
- Mail automation controller test:
  - Command: `cd ecm-core && mvn -q -Dtest=MailAutomationControllerTest test`
  - Result: ✅ Passed

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed

## Playwright E2E
- Mail automation targeted:
  - Command:
    - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/mail-automation.spec.ts`
  - Result: ✅ 1 passed (~12.5s)
- Full regression:
  - Command:
    - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Result: ✅ 22 passed (~5.2m)

## Manual Notes
- Mail Automation now has a "Fetch Diagnostics (Dry Run)" card that:
  - Runs without ingesting content.
  - Shows skip reasons and per-folder visibility.

