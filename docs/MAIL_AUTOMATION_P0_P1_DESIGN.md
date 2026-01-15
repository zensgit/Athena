# Mail Automation P0/P1 Design

## Scope
- Add mail processing idempotency and audit trail.
- Expand mail rule filters (recipient/body/attachments/age) and mailbox folder targeting.
- Separate processing scope (attachments/email) from post-processing mail actions.
- Update API + UI to configure the new fields.

## Data Model
### `mail_accounts`
- New table to back `MailAccount` (IMAP host/port/security, enabled, poll interval).
- Base audit columns consistent with other tables.

### `mail_rules`
New columns to support expanded filters and actions:
- `folder`: IMAP mailbox folder, default `INBOX`.
- `to_filter`, `attachment_filename_include`, `attachment_filename_exclude`.
- `max_age_days`, `include_inline_attachments`.
- `mail_action`, `mail_action_param` (post-processing action).

### `mail_processed_messages`
Tracks processed messages for idempotency and auditing:
- `account_id`, `rule_id`, `folder`, `uid` (unique).
- `subject`, `received_at`, `processed_at`, `status`, `error_message`.

Migration: `ecm-core/src/main/resources/db/changelog/changes/014-add-mail-automation-tables.xml`.

## Rule Matching
Matching happens in priority order and includes:
- Subject/from/to/body regex filters.
- Attachment filename include/exclude (wildcard support).
- Max age filter (received date).
Attachment filters are evaluated at the message level and again per attachment for ingestion.

## Processing Flow
1. Fetch unread messages from each configured mailbox folder.
2. Skip already-processed messages based on `(account_id, folder, uid)`.
3. Match the first rule that satisfies all filters.
4. Process content based on `actionType`:
   - `ATTACHMENTS_ONLY`: ingest matching attachments.
   - `METADATA_ONLY`: ingest full email as `.eml`.
   - `EVERYTHING`: ingest `.eml` + attachments.
5. Apply post-processing `mail_action`:
   - `MARK_READ`, `FLAG`, `DELETE`, `MOVE`, `TAG`, `NONE`.
6. Record processing outcome in `mail_processed_messages`.

## UI Updates
Admin Mail Automation now exposes:
- Mailbox folder per rule.
- To/body/attachment filters.
- Max age + inline attachment toggle.
- Post-processing mail action + parameter.

## Notes / Limitations
- OAuth-based IMAP auth is not implemented in this iteration.
- Mail action `TAG` sets an IMAP keyword/flag; support varies by server.
- Unmatched messages are left untouched and remain eligible for future rules.
- Mail fetcher runs without a user security context; folder assignment requires create permission for the effective user.
