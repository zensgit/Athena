# Mail Automation P27 — Replay Failed Processed Items

Date: 2026-02-06

## Goal
Allow operators to replay a failed processed-mail entry directly from diagnostics without re-running full mailbox fetch.

## Design
### Backend
- Add endpoint:
  - `POST /api/v1/integration/mail/processed/{id}/replay`
- Service flow:
  1. Load processed-mail row by id.
  2. Resolve account + folder.
  3. Reconnect IMAP store and locate message by UID.
  4. Re-run existing message processing pipeline in replay mode.
- Replay mode behavior:
  - Bypasses duplicate-skip guard in `processMessage`.
  - Keeps uniqueness by upserting processed record (`account+folder+uid`) rather than inserting duplicate rows.
- Rule resolution:
  - Prefer original `ruleId` when still available among enabled account rules.
  - Fallback to normal account-enabled ordered rules.
- Audit:
  - `MAIL_PROCESSED_REPLAY` event with replay outcome details.

### Frontend
- Add diagnostics row action for `ERROR` entries:
  - `Replay` button in Error Message column.
- On replay completion:
  - show success/info/warn/error toast based on response.
  - refresh diagnostics + retention summaries.

### API Contract
- New response shape (`MailReplayResult`):
  - `processedMailId`
  - `attempted`
  - `processed`
  - `message`
  - `replayStatus`

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/integration/mail/repository/ProcessedMailRepository.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-frontend/src/services/mailAutomationService.ts`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/e2e/mail-automation.spec.ts`
