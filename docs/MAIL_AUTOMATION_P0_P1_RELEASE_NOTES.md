# Mail Automation P0/P1 Release Notes

## Summary
Mail automation now supports richer rule filters, mailbox folder targeting, idempotent processing tracking, and configurable post-processing mail actions. UI and API were updated to expose the new fields.

## Backend Highlights
- Added `mail_processed_messages` for idempotent processing and audit trails.
- Expanded `mail_rules` to support folder targeting, to/body filters, attachment include/exclude, max age, inline toggle, and post-processing actions.
- Rule matching uses regex for text fields and wildcard matching for attachment filenames.
- Processing supports `ATTACHMENTS_ONLY`, `METADATA_ONLY` (.eml), or `EVERYTHING` (both).
- Post-processing mail actions: `MARK_READ`, `FLAG`, `DELETE`, `MOVE`, `TAG`, `NONE`.

## Frontend Highlights
- Mail Automation page exposes new rule fields (folder, to/body filters, attachment filters, max age, inline toggle).
- Mail action and optional parameter are now configurable per rule.

## Data & Migrations
- Liquibase changeSet `014-add-mail-automation-tables.xml` adds `mail_accounts`, `mail_rules`, `mail_processed_messages` with indexes.
- Preconditions guard against existing tables and mark changeSets as ran when tables already exist.

## Ops Notes
- IMAP security options: `NONE`, `SSL`, `STARTTLS`.
- `TAG` action maps to IMAP keywords; server support varies.
- Mail fetcher runs without a user security context; folder assignment requires create permission for the effective user.

## Verification
- `cd ecm-core && mvn test`
- `cd ecm-frontend && npm run lint`
- `cd ecm-frontend && CI=true npm test`
- GreenMail E2E (attachment + eml ingest)

See `docs/MAIL_AUTOMATION_P0_P1_VERIFICATION.md` for execution details and artifacts.
