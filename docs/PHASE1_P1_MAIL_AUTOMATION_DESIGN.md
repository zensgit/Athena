# Mail Automation P1 — UTF-8 Login Fallback + Processing Scope Labels

Date: 2026-02-06

## Context
Reference review:
- `reference-projects/paperless-ngx/src/paperless_mail/mail.py` uses a UTF-8 aware fallback (`login_utf8`) when passwords are not ASCII.
- `reference-projects/paperless-ngx/src/paperless_mail/models.py` documents processing scope that distinguishes full email (.eml) vs attachments-only.

Athena already supports `.eml` ingestion via `EmailIngestionService` and `MailFetcherService` when `MailActionType.EVERYTHING`, but the UI labels are raw enum values and don't explain the behavior.

## Goals
1. Improve IMAP authentication reliability for non-ASCII passwords by retrying with a UTF-8-friendly mechanism.
2. Clarify processing scope options to reflect the `.eml` + attachments behavior.

## Non-Goals
- Add new MailActionType values or change ingestion behavior.
- Modify mail rule matching logic.

## Design
### 1) UTF-8 password fallback
- Detect non-ASCII passwords.
- Attempt standard login first.
- If authentication fails and password is non-ASCII, retry with AUTH=PLAIN and enable UTF-8 related properties.

**Implementation**
- `MailFetcherService.connect` now retries the connection with:
  - `mail.imap.auth.login.disable=true`
  - `mail.imap.allowutf8=true`
  - `mail.mime.allowutf8=true`

### 2) Processing scope labels
- Replace raw enum display with readable labels.
- Add inline helper text to explain which documents are created:
  - Attachments only
  - Email (.eml) only
  - Email (.eml) + attachments

**Implementation**
- `MailAutomationPage` adds label/description maps and uses `FormHelperText` for the processing scope selector.

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`

