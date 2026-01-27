# Design: Mail Automation Fetch Diagnostics (Dry Run) (2026-01-27)

## Goals
- Make "why no mail was ingested" diagnosable without mutating mailbox state.
- Surface skip reasons, rule match coverage, and folder-level visibility.

## Backend Design
- New dry-run diagnostics entry point:
  - `MailFetcherService.fetchAllAccountsDebug(force, maxMessagesPerFolder)`
- New response model (debug-only):
  - `MailFetchDebugResult`
  - `MailFetchDebugAccountResult`
  - `MailFetchDebugFolderResult`
- Safety guarantees for dry runs:
  - Folders are opened `READ_ONLY`.
  - No ingestion is attempted.
  - No mail actions (mark read/move/delete/tag) are applied.
  - No processed-mail records are written.
- Skip reasons are aggregated at three levels:
  - Per-folder
  - Per-account
  - Global run
- New endpoint:
  - `POST /api/v1/integration/mail/fetch/debug`
  - Query params:
    - `force` (default `true`)
    - `maxMessagesPerFolder` (optional limit)
- New configuration:
  - `ecm.mail.fetcher.debug.max-messages-per-folder` (default `200`)

## Frontend Design
- New service method:
  - `mailAutomationService.triggerFetchDebug(...)`
- Mail Automation page changes:
  - New "Fetch Diagnostics (Dry Run)" card above Mail Accounts.
  - Admin can set "Max messages / folder" and run diagnostics.
  - Results show:
    - Summary chips (attempted/found/matched/processable/skipped/errors)
    - Top global skip reasons
    - Per-account skip reasons and rule match counts
    - Per-folder summary table

## Trade-offs
- Dry run predicts "processable" based on action type and attachments but does not execute ingestion.
- Debug runs still connect to the mailbox and read message structure.

## Files
- Backend:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- Frontend:
  - `ecm-frontend/src/services/mailAutomationService.ts`
  - `ecm-frontend/src/pages/MailAutomationPage.tsx`
  - `ecm-frontend/e2e/mail-automation.spec.ts`

