# Phase 1 P62 - Mail Traceability: Per-Message Ingested Documents (Design) - 2026-02-09

## Problem
Athena already exposes Mail Automation diagnostics:
- **Processed Messages** (account/rule/folder/uid/subject/status/error)
- **Mail Documents** (documents created by mail ingestion)

But in the UI, a processed message can only show a *best-effort* “linked doc” derived from the **recent** mail documents list.
This has limitations:
- Multiple attachments can create multiple documents, but only 0/1 is shown.
- If the linked document is not in the current diagnostics window, the UI cannot link it.
- Troubleshooting requires manual correlation across tables.

## Goal
From a processed message row, allow admins to reliably answer:
- “Which document(s) did this message produce?”
- “Where were they stored (path)?”

Without introducing new mail-state schema, and without exposing sensitive credentials.

## Approach
Use the existing correlation keys already persisted in two places:
- `mail_processed_messages` table (ProcessedMail): `account_id`, `folder`, `uid`
- Node JSON properties on ingested documents:
  - `mail:source = true`
  - `mail:accountId`
  - `mail:folder`
  - `mail:uid`

Add:
1. **Backend API**: list documents for a processed mail record by id.
2. **Frontend UX**: a per-row “View ingested documents” action that opens a dialog showing all matched documents.
3. **E2E coverage**: Playwright test that validates the dialog opens and renders either a table, an empty state, or a load-error message.

## API Change
New endpoint:
- `GET /api/v1/integration/mail/processed/{id}/documents?limit=200`

Response:
- `MailDocumentDiagnosticItem[]` (same shape as diagnostics `recentDocuments`)

Notes:
- The endpoint is **admin-only** (same as other Mail Automation endpoints).
- Limit is clamped server-side (default 50, max 200).

## Data Access
New repository query (native SQL) filters documents by:
- `n.properties ->> 'mail:source' = 'true'`
- `mail:accountId`, `mail:folder`, `mail:uid`

Ordering:
- `created_date DESC`

## UI/UX
Processed Messages table:
- Always show a `List` icon button (aria-label: `view ingested documents`).
- When clicked:
  - Open dialog
  - Fetch from the new backend endpoint
  - Render:
    - Loading spinner, then
    - A table of documents (Created/Name/Path + Open/Similar actions), or
    - Empty-state text if no documents, or
    - Error text if the fetch fails

## Edge Cases
- Processed mail exists but no ingested documents:
  - Message matched a rule, but no attachments met filters, or action type excludes attachments.
- Older documents missing `mail:folder`/`mail:uid` properties:
  - Not expected for current pipeline (properties are written on ingest), but will simply return 0 results.
- Collisions:
  - UI correlation key is updated to include `accountId + folder + uid` to reduce ambiguity.

