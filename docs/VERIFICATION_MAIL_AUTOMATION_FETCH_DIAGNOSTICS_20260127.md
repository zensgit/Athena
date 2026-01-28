# Verification: Mail Automation Fetch Diagnostics (Dry Run) (2026-01-27)

## Backend Tests
- Full backend suite:
  - Command: `cd ecm-core && mvn -q test`
  - Result: ✅ Passed
- Mail automation controller test:
  - Command: `cd ecm-core && mvn -q -Dtest=MailAutomationControllerTest test`
  - Result: ✅ Passed
- Mail automation controller (account + security + diagnostics):
  - Command: `cd ecm-core && mvn -q -Dtest=MailAutomationControllerTest,MailAutomationControllerSecurityTest,MailAutomationControllerDiagnosticsTest test`
  - Result: ✅ Passed
- Mail automation diagnostics security test:
  - Command: `cd ecm-core && mvn -q -Dtest=MailAutomationControllerSecurityTest test`
  - Result: ✅ Passed
- Mail automation diagnostics sample response test:
  - Command: `cd ecm-core && mvn -q -Dtest=MailAutomationControllerDiagnosticsTest test`
  - Result: ✅ Passed
- Mail diagnostics service mapping + limit clamp test:
  - Command: `cd ecm-core && mvn -q -Dtest=MailFetcherServiceDiagnosticsTest test`
  - Result: ✅ Passed

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed

## Playwright E2E
- Mail automation targeted:
  - Command:
    - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/mail-automation.spec.ts`
  - Result: ✅ 2 passed (~33s)
- UI smoke (mail automation actions):
  - Command:
    - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "Mail automation actions"`
  - Result: ✅ 1 passed (~40s)
- Full regression:
  - Command:
    - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Result: ✅ 23 passed (~5.6m)
- New coverage:
  - Mail automation folder discovery UI (`List Folders`) and rule dialog helper text.
  - Recent activity tables no longer interfere with rule editing row selection.
  - CSV export button triggers backend export response and respects column toggles (Subject/Path disabled).

## Manual Notes
- Mail Automation now has a "Fetch Diagnostics (Dry Run)" card that:
  - Runs without ingesting content.
  - Shows skip reasons and per-folder visibility.
- Mail Automation now also has a "Recent Mail Activity" card that:
  - Shows recent processed messages (from processed-mail records).
  - Shows recent ingested documents tagged with mail provenance.
  - Refreshes after `Trigger Fetch` and `Run Diagnostics`.
  - Supports filtering by account and rule.
  - Supports exporting a CSV from the UI (server-generated).
  - UI indicates exports are capped at the same limit shown in the table.
  - Export options allow selecting which sections/columns to include.
  - Export options persist across reloads (localStorage).

## Recent Activity Diagnostics API (2026-01-27)
- Command:
  - Token acquisition (Keycloak):
    - `POST http://localhost:8180/realms/ecm/protocol/openid-connect/token`
  - Diagnostics:
    - `GET http://localhost:7700/api/v1/integration/mail/diagnostics?limit=5`
  - Result:
    - ✅ HTTP 200
    - `recentProcessed` returned populated entries.
    - `recentDocuments` may be empty until new mail is ingested after the
      provenance tagging change.

## Diagnostics Export API (2026-01-28)
- Endpoint:
  - `GET /api/v1/integration/mail/diagnostics/export`
- Behavior:
  - Returns CSV with metadata header + "Processed Messages" + "Mail Documents".
  - Optional query params for field selection:
    - `includeProcessed`, `includeDocuments`
    - `includeSubject`, `includeError`
    - `includePath`, `includeMimeType`, `includeFileSize`
  - Audit log event:
    - `MAIL_DIAGNOSTICS_EXPORTED` recorded with filters + field toggles (validated via controller test).

## UI Confirmation (2026-01-28)
- Manual steps:
  - Open `http://localhost:3000/admin/mail`
  - In "Recent Mail Activity", click `Refresh`
  - Confirm at least one row appears under **Mail Documents**
- Screenshot:
  - `ecm-frontend/test-results/tmp-mail-activity.png`

## Automated Diagnostics Script (2026-01-28)
- Script:
  - `./scripts/verify-mail-diagnostics.sh`
- Defaults:
  - `LIMIT=5`, `EXPECTED_MIN=1`
- Result:
  - ✅ `limit=5 recentProcessed=5 recentDocuments=1`
- Backend logs now include diagnostic summaries, e.g.:
  - `Mail debug folder <account>:INBOX found=...`
  - `Mail debug summary for <account>: found=..., matched=..., processable=...`
- Folder coverage:
  - Mail rule folder supports comma-separated folders (e.g. `INBOX,[Gmail]/All Mail`).
  - Both normal fetch and debug fetch evaluate all listed folders.

## Observed Diagnostics (2026-01-27)
- Command (API debug fetch):
  - `POST /api/v1/integration/mail/fetch/debug?force=true&maxMessagesPerFolder=200`
- Result snapshot:
  - `foundMessages=0` for `gmail-imap` across `ECM-TEST` and `INBOX`
  - Logs:
    - `Mail debug folder gmail-imap:ECM-TEST found=0, scanned=0, matched=0, processable=0, skipped=0, errors=0`
    - `Mail debug folder gmail-imap:INBOX found=0, scanned=0, matched=0, processable=0, skipped=0, errors=0`

## Folder Discovery
- Folder list endpoint:
  - `GET /api/v1/integration/mail/accounts/{id}/folders`
- This helps confirm whether rule folder names exist (e.g., when `folder_missing` appears in diagnostics).
- Verified via API:
  - Result: ✅ Returned 18 folders.
  - Notable folders present:
    - `ECM-TEST`
    - `INBOX`
    - `[Gmail]/所有邮件` (Gmail "All Mail" in this mailbox locale)

## Runtime Rule Fix (2026-01-27)
- Issue:
  - Diagnostics previously showed `folder_missing` for `[Gmail]/All Mail`.
- Action:
  - Updated rule folder to `ECM-TEST,INBOX` (valid folders discovered via the new endpoint).
- Result:
  - `folder_missing` skip reason no longer appears in debug summaries.

## Malformed Message Resilience (2026-01-27)
- Diagnostic technique:
  - Temporarily included `[Gmail]/所有邮件` in the rule folder for a debug run only, then reverted to `ECM-TEST,INBOX`.
- Before resilience changes:
  - Debug logs showed:
    - `Mail debug message processing failed: Unknown encoding: HEXA`
  - Skip reason: `message_error`
- After resilience changes:
  - Debug summary (with All Mail temporarily included):
    - `foundMessages=1`
    - `errorMessages=0`
    - `skippedMessages=1`
    - Skip reasons: `no_rule=1`
  - Interpretation:
    - A malformed/oddly encoded message no longer breaks diagnostics.
    - The remaining skip reason is actionable (likely no attachments, so rule does not match).
