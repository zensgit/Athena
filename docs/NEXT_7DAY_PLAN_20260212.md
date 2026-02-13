# Next 7-Day Delivery Plan (2026-02-12)

This plan is grounded in:
- `docs/ALFRESCO_GAP_ANALYSIS_20260129.md` (Alfresco benchmark gaps)
- `docs/ROLLUP_NEXT_ITERATION_SUGGESTIONS_20260205.md` (next iteration backlog)
- Current Phase 1 baseline + regression gates

## Guiding Principles

- Each day delivers a vertical slice: **feature + tests + docs**.
- Every slice ships with:
  - one design MD
  - one verification MD
  - at least one automated check (Playwright E2E or backend tests)
- Prefer additive changes; avoid breaking public APIs unless explicitly documented.
- Never commit secrets; OAuth tokens stay in env/config only.

## Current Baseline (Already Green)

- Search continuity regression gate: `docs/PHASE1_P104_SEARCH_CONTINUITY_REGRESSION_GATE_VERIFICATION_20260212.md`
- Search fallback governance parity fix: `docs/PHASE1_P105_SEARCH_FALLBACK_CRITERIA_KEY_PARITY_*_20260212.md`

## Delivery Outline (7 days)

### Day 1 (P0) — Mail Diagnostics Run ID + UI Surfacing

Scope:
- Backend + Frontend

Problem:
- Diagnostics/debug runs are hard to correlate across UI/export/logs.

Implementation:
1. Backend: introduce a `runId` (UUID string) for:
   - `POST /api/v1/integration/mail/fetch/debug`
   - `POST /api/v1/integration/mail/rules/{id}/preview`
   - `GET /api/v1/integration/mail/diagnostics` (optional: include `requestId` header for correlation)
2. Backend: structured logging (single-line JSON-ish key-value) for each run:
   - `runId`, `accountId`, `ruleId`, `maxMessagesPerFolder`, `force`, duration, summary counts.
3. Frontend (`MailAutomationPage`):
   - display `runId` next to the diagnostics summary and allow "Copy run id"
   - include `runId` in the CSV export filename or add as first row in export header.

Acceptance:
- Running "Run Diagnostics" or "Run Preview" shows a `runId` that can be used to find the corresponding server log line(s).

Verification:
- Add/extend Playwright: assert runId appears after diagnostics/preview.
- Backend test: unit test that runId is non-empty and stable within a single response.

Docs:
- `docs/PHASE2_D1_MAIL_DIAGNOSTICS_RUNID_DESIGN_20260212.md`
- `docs/PHASE2_D1_MAIL_DIAGNOSTICS_RUNID_VERIFICATION_20260212.md`

### Day 2 (P0) — Permission Template Diff Export JSON + Audit Event

Scope:
- Backend + Frontend + Audit

Implementation:
1. Backend: add JSON export alongside CSV for permission template version diffs:
   - Option A (recommended): `GET /api/v1/security/permission-templates/{id}/versions/diff/export?from=...&to=...&format=json|csv`
   - JSON payload includes: added/removed/changed entries with authority/permission/allowed/inherited.
2. Backend: write an audit event when a diff export is performed:
   - eventType: `PERMISSION_TEMPLATE_DIFF_EXPORT`
   - metadata: templateId, fromVersionId, toVersionId, format
3. Frontend (`PermissionTemplatesPage`):
   - add `Export JSON` button next to `Export CSV` in compare dialog
   - download file with meaningful name: `permission-template-diff-{template}-{from}-{to}.json`

Verification:
- Playwright: compare two versions, export JSON, assert download has `.json` and contains expected keys.
- Backend: controller/service tests for `format=json`.

Docs:
- `docs/PHASE2_D2_PERMISSION_TEMPLATE_DIFF_JSON_DESIGN_20260212.md`
- `docs/PHASE2_D2_PERMISSION_TEMPLATE_DIFF_JSON_VERIFICATION_20260212.md`

### Day 3 (P0) — Preview Retry Per-Item Actions (Search + Advanced Search)

Scope:
- Frontend (and small API contract checks)

Implementation:
1. Frontend:
   - In Search results cards/rows, add per-item actions for documents:
     - `Retry preview` -> `POST /api/v1/documents/{id}/preview/queue?force=false`
     - `Force rebuild` -> `POST /api/v1/documents/{id}/preview/queue?force=true`
   - Only show when the item is retryable (exclude unsupported-only failures).
   - Surface immediate feedback: toast + update per-row status chip (queued/nextAttemptAt if available).
2. Optional: extend API response parsing if queue endpoint returns richer status.

Verification:
- Playwright: seed a result with `previewStatus=FAILED` retryable, click retry, assert success toast and status chip changes.

Docs:
- `docs/PHASE2_D3_PREVIEW_RETRY_PER_ITEM_DESIGN_20260212.md`
- `docs/PHASE2_D3_PREVIEW_RETRY_PER_ITEM_VERIFICATION_20260212.md`

### Day 4 (P1) — Search Snippet Enrichment (Path + Creator + Match Fields)

Scope:
- Frontend

Implementation:
1. Search cards: if present, show:
   - file path (breadcrumb-style, truncated)
   - createdBy
   - matchFields chips (already computed) in a compact row
2. Keep layout stable: avoid pushing primary title/preview actions below the fold.

Verification:
- Playwright: search for a known term; assert snippet contains `path` and `createdBy` fields.

Docs:
- `docs/PHASE2_D4_SEARCH_SNIPPET_ENRICHMENT_DESIGN_20260212.md`
- `docs/PHASE2_D4_SEARCH_SNIPPET_ENRICHMENT_VERIFICATION_20260212.md`

### Day 5 (P1) — Mail Reporting Scheduled Export (Optional if Time Allows)

Scope:
- Backend + Frontend + Data (if persistence required)

Implementation (minimal viable):
1. Backend:
   - scheduled job (Spring scheduler) that runs daily and writes the report CSV to a configured folderId.
   - configuration-only:
     - enable flag
     - folderId target
     - schedule cron
2. Frontend:
   - settings panel for admin to view current schedule config (read-only initially).

Verification:
- Unit test: scheduler triggers service with correct window and folderId.
- Manual: run scheduler method directly in dev profile.

Docs:
- `docs/PHASE2_D5_MAIL_REPORT_SCHEDULE_EXPORT_DESIGN_20260212.md`
- `docs/PHASE2_D5_MAIL_REPORT_SCHEDULE_EXPORT_VERIFICATION_20260212.md`

### Day 6 (Tech Debt) — Docs Index + Regression Gate Consolidation

Scope:
- Docs + Tooling

Implementation:
1. Create a single index doc linking Phase docs by topic (Mail/Search/Preview/Permissions/Audit/Version).
2. Add/extend a "weekly regression" command list that runs the highest-signal E2E specs.

Verification:
- `rg`/links sanity + Playwright regression gate command works on a clean run.

Docs:
- `docs/DOCS_INDEX_20260212.md`
- `docs/WEEKLY_REGRESSION_UPDATE_20260212.md`

### Day 7 (Ops) — CI Guardrails (Frontend + Backend)

Scope:
- Tooling + CI (GitHub Actions)

Implementation:
1. Add CI workflow(s):
   - frontend: `npm ci`, `npm test`, `npm run lint`
   - backend: `mvn test`
2. Add a guard to skip mail OAuth E2E when `.env.mail` is absent, with a clear log line.

Verification:
- Local `act` (optional) or documented `gh` check expectations.

Docs:
- `docs/PHASE2_D7_CI_GUARDRAILS_DESIGN_20260212.md`
- `docs/PHASE2_D7_CI_GUARDRAILS_VERIFICATION_20260212.md`

## Default Execution Order

1. Day 1
2. Day 2
3. Day 3
4. Day 4
5. Day 5 (optional)
6. Day 6
7. Day 7

