# Verification: Mail Automation Test Connection + Fetch Summary (2026-01-24)

## Scope
- Test connection API endpoint.
- Manual fetch summary response and UI toast integration.
- UI “Test connection” action.

## Environment
- Repo: `/Users/huazhou/Downloads/Github/Athena`
- Backend: `ecm-core`
- Frontend: `ecm-frontend`

## Test Steps (Manual)
1. Start backend and frontend.
   - `cd ecm-core && mvn spring-boot:run`
   - `cd ecm-frontend && npm start`
2. Open Mail Automation page.
3. Click “Test connection” for a configured account.
   - Expect: toast with success/failure and duration.
4. Click “Trigger Fetch”.
   - Expect: toast with processed/matched/unread/skipped counts and duration.
   - If errors occur, toast severity is warning and includes error counts.

## API Checks (Optional)
- Test connection:
  - `POST /api/v1/integration/mail/accounts/{id}/test`
  - Expect JSON: `{ success, message, durationMs }`
- Manual fetch:
  - `POST /api/v1/integration/mail/fetch`
  - Expect JSON summary with counts and duration.

## Results
- API: PASS
  - `POST /api/v1/integration/mail/accounts/{id}/test` returned `{"success":true,"message":"Connected","durationMs":1784}`.
  - `POST /api/v1/integration/mail/fetch` returned `{"accounts":1,"attemptedAccounts":1,"skippedAccounts":0,"accountErrors":0,"foundMessages":0,"matchedMessages":0,"processedMessages":0,"skippedMessages":0,"errorMessages":0,"durationMs":3035}`.
- UI: PASS (Playwright)
  - `ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/mail-automation.spec.ts --config playwright.config.ts`
  - Verified: Mail Automation page loads at `/admin/mail`, “Test connection” toast renders, and “Trigger Fetch” toast renders.

## Full E2E Run (Context)
- Initial run (before storage fix):
  - Result: 4 passed / 17 failed (12.7m)
  - Primary failure cause: document uploads failed with `Content storage failed: /var/ecm/content/2026/01/25` (HTTP 400).
- After storage permission fix + entrypoint hardening:
  - Result: 21 passed (5.0m)
  - Mail Automation test: PASS (`e2e/mail-automation.spec.ts`, `ui-smoke.spec.ts` mail automation actions).
