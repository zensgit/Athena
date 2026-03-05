# Phase 136: Mail Diagnostics Export RunId Traceability

## Date
2026-03-05

## Background
- Mail Automation already surfaces `runId` for fetch/debug/preview flows.
- Diagnostics CSV export still lacked explicit run correlation in both filename and CSV metadata.
- Audit log also did not carry the selected `runId`, which made cross-system triage slower.

## Goals
1. Preserve existing export behavior when no `runId` is available.
2. Pass optional `runId` from frontend export action to backend export endpoint.
3. Include `RunId` in diagnostics CSV metadata and diagnostics export audit details.
4. Keep API/UI changes backward compatible.

## Changes

### Frontend
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
  - added diagnostics export `runId` resolver:
    - prefer debug/fetch run ids when available
    - use latest timestamp between debug run and fetch run when both exist
  - added filename-safe runId sanitizer
  - diagnostics export filename now uses:
    - `mail-diagnostics-<runId>-<timestamp>.csv` when runId exists
    - fallback to old `mail-diagnostics-<timestamp>.csv` when missing
  - export API call now forwards `runId`.
- `ecm-frontend/src/services/mailAutomationService.ts`
  - `exportDiagnosticsCsv(...)` signature extended with optional `runId`.
  - forwards `runId` as query parameter when set.

### Backend
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
  - `GET /integration/mail/diagnostics/export` now accepts optional `runId`.
  - forwards `runId` to `MailFetcherService.exportDiagnosticsCsv(...)`.
  - includes `runId` in diagnostics export audit detail payload.
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
  - export method signatures extended with optional `runId`.
  - CSV header now includes `RunId,<value>` (defaults to `NONE` when absent).

### Tests
- `ecm-core/src/test/java/com/ecm/core/integration/mail/service/MailFetcherServiceDiagnosticsTest.java`
  - updated invocation signature and added assertion for `RunId` CSV header row.
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerSecurityTest.java`
  - updated mock/verify method signatures for added `runId`.
  - added assertion that audit detail contains `runId=<value>`.

## Compatibility
- Existing clients remain compatible: `runId` is optional in request path.
- CSV export continues to work with or without `runId`.
- No DB schema change.
