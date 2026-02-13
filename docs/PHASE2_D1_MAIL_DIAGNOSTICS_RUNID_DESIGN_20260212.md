# Phase 2 Day 1 (P0) - Mail Diagnostics Run ID (Design)

Date: 2026-02-12

## Goal

Make mail ingestion runs (trigger fetch + diagnostics/debug + rule preview) easy to correlate across:
- UI actions
- API responses
- backend logs

This enables faster triage when a mailbox requires OAuth reconnect, rules mis-match, or ingestion has transient errors.

## Scope

- Backend: `ecm-core`
- Frontend: `ecm-frontend`
- Automation: Playwright E2E

Out of scope (future):
- Persisting run summaries beyond the existing fetch summary and processed-mail records.
- Adding a dedicated export artifact for debug runs (JSON/CSV).

## Backend Changes

### New/Extended Fields

Add `runId` (UUID string) to responses:

1. Fetch run summary (trigger fetch):
   - `MailFetchSummary.runId`
2. Fetch diagnostics (debug run):
   - `MailFetchDebugResult.summary.runId`
3. Rule preview:
   - `MailRulePreviewResult.runId`

`runId` is generated once per request and reused for all log lines produced by that run.

### Logging

Add structured-ish correlation log lines:
- `Starting mail fetch (runId=...) ...`
- `Mail fetch completed (runId=...) ...`
- `Starting mail fetch debug run (runId=...) ...`
- `Mail fetch debug run completed (runId=...) ...`
- `Starting mail rule preview (runId=...) ...`
- `Mail rule preview completed (runId=...) ...`

This keeps troubleshooting flow simple:
1) copy runId from UI,
2) grep logs for that runId.

### Files

- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`

## Frontend Changes

### UI Surfacing

In `MailAutomation` page, display a "Run XXXXXXXX" chip (first 8 hex chars) when `runId` is present:
- Trigger fetch summary chip: `lastFetchSummary.runId`
- Debug/diagnostics result chip: `debugResult.summary.runId`
- Rule preview result chip: `previewResult.runId`

For the diagnostics + preview chips:
- chip is click-to-copy (clipboard)
- tooltip shows the full runId to copy

### API Client Types

Extend TypeScript models to accept the optional field (backward compatible with older servers):
- `MailFetchSummary.runId?: string | null`
- `MailRulePreviewResult.runId?: string | null`

### Files

- `ecm-frontend/src/services/mailAutomationService.ts`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/pages/AdminDashboard.tsx` (small UX parity: show run chip in summary panel)

## Automated Verification

Playwright E2E extends existing mail automation spec:
- After "Trigger Fetch" and "Run Diagnostics", assert at least one `Run [0-9a-f]{8}` chip is visible in the diagnostics card.

Backend unit tests:
- `fetchAllAccounts(true)` returns `runId` (UUID) even when no accounts exist.
- `fetchAllAccountsDebug(true, ...)` returns `runId` (UUID) even when no accounts exist.

## Compatibility / Migration

- Backward compatible:
  - Frontend treats `runId` as optional and only renders chips when present.
- No schema change.
- No secret material added to code or docs.

