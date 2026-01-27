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
- New diagnostic logging summaries:
  - Per-folder debug summary
  - Per-account debug summary (including top skip reasons and top rule matches)
  - Global top skip reasons when present
- Safety guarantees for dry runs:
  - Folders are opened `READ_ONLY`.
  - No ingestion is attempted.
  - No mail actions (mark read/move/delete/tag) are applied.
  - No processed-mail records are written.
- Folder coverage:
  - Mail rule folder now supports comma-separated folder names (e.g. `INBOX,[Gmail]/All Mail`).
  - Both normal fetch and debug fetch evaluate all listed folders.
- Skip reasons are aggregated at three levels:
  - Per-folder
  - Per-account
  - Global run
- Resilience improvements:
  - Malformed header encodings (e.g., unknown charset/encoding tokens) no longer fail the entire debug run.
  - Multipart read failures are handled per-part with debug logs and safe fallbacks.
  - This shifts outcomes from `message_error` to more actionable skip reasons like `no_rule` or `no_content`.
- New endpoint:
  - `POST /api/v1/integration/mail/fetch/debug`
  - Query params:
    - `force` (default `true`)
    - `maxMessagesPerFolder` (optional limit)
- New recent-activity diagnostics endpoint:
  - `GET /api/v1/integration/mail/diagnostics?limit=25`
  - Returns:
    - Recent processed mail records (from `processed_mail`)
    - Recent ingested mail documents (from `nodes`/`documents`)
  - Guardrails:
    - Admin-only
    - Uses system auth context internally for consistent visibility
- New folder discovery endpoint:
  - `GET /api/v1/integration/mail/accounts/{id}/folders`
  - Purpose: list available IMAP folders to avoid misconfigured folder names.
- New configuration:
  - `ecm.mail.fetcher.debug.max-messages-per-folder` (default `200`)
- Mail provenance tagging during ingestion:
  - When ingesting mail content or attachments, we persist:
    - `mail:source=true`
    - `mail:accountId`, `mail:accountName`
    - `mail:ruleId`, `mail:ruleName`
    - `mail:folder`, `mail:uid`
    - `mail:processedAt`
  - This enables fast, queryable diagnostics without extra tables.

## Frontend Design
- New service method:
  - `mailAutomationService.triggerFetchDebug(...)`
- Mail Automation page changes:
  - New "Fetch Diagnostics (Dry Run)" card above Mail Accounts.
  - New "Recent Mail Activity" card under diagnostics.
  - Admin can set "Max messages / folder" and run diagnostics.
  - Admin can select an account and list available folders.
  - Results show:
    - Summary chips (attempted/found/matched/processable/skipped/errors)
    - Top global skip reasons
    - Per-account skip reasons and rule match counts
    - Per-folder summary table
    - Recent processed messages (status/account/rule/folder/uid/subject)
    - Recent ingested mail documents (created/name/path/account/rule/folder/uid)
- E2E test stability adjustment:
  - Mail rule row selection now scopes to the "Mail Rules" card to avoid
    collisions with the new recent-activity tables.

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
