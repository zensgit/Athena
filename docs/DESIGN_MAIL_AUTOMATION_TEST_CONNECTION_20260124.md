# Mail Automation Test Connection + Fetch Summary Design (2026-01-24)

## Goal
Provide a clear way to validate mail account connectivity and return meaningful results when triggering a manual fetch.

## Scope
- Backend: add a connection test endpoint and return a fetch summary for manual fetch.
- Frontend: add a per-account “Test connection” action and show fetch summary in UI notifications.

## Non-goals
- Changing mail rule processing logic or scheduling cadence.
- Persisting connection test results in the database.

## Backend Changes
- `POST /api/v1/integration/mail/accounts/{id}/test`
  - Returns `MailConnectionTestResult { success, message, durationMs }`.
  - Uses existing `MailFetcherService.connect()` logic; closes store after test.
- `POST /api/v1/integration/mail/fetch`
  - Now returns `MailFetchSummary` with counts and duration.

### Summary Fields
`accounts`, `attemptedAccounts`, `skippedAccounts`, `accountErrors`, `foundMessages`, `matchedMessages`,
`processedMessages`, `skippedMessages`, `errorMessages`, `durationMs`.

## Frontend Changes
- Add `testConnection(accountId)` to mail automation service.
- Update `triggerFetch()` to return `MailFetchSummary`.
- UI:
  - Add “Test connection” icon button per mail account.
  - Show spinner while testing.
  - Show success/error toast with duration.
  - Trigger fetch toast includes summary with error signaling.

## UX Notes
- If errors occur during fetch, show a warning toast including error counts.
- Otherwise show a success toast with processed/matched/found counts and duration.

## Risks
- OAuth tokens may expire; test connection surfaces message without persisting.
- Gmail/Outlook throttling may increase test latency; duration is displayed for clarity.

## Rollout
- No config changes required. API is backward compatible with existing UI once updated.
