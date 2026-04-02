# Phase 262 - Preview Queue Declined WindowHours Filter (Dev)

Date: 2026-03-10  
Stream: Day2-7 Stream B (queue governance)

## Objective

Extend declined queue governance with a time-window filter so operators can scope actions to recent declines only:

1. add `windowHours` filter (`ANY` or `1..720`);
2. propagate filter across summary/export/requeue/dry-run/clear;
3. include filter context in response payloads, CSV export, and audit logs.

## Backend changes

File:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

### 1) New `windowHours` request parameter on declined endpoints

Updated endpoints:
- `GET /api/v1/preview/diagnostics/queue/declined`
- `GET /api/v1/preview/diagnostics/queue/declined/export`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run`
- `POST /api/v1/preview/diagnostics/queue/declined/clear`

Added normalization:
- `normalizeQueueDeclinedWindowHours(Integer windowHours)`
  - `null` / `<=0` => `ANY` (`null`)
  - `>0` => clamp to `1..720`

### 2) Declined filtering by `declinedAt`

Updated `mapQueueDeclinedItems(...)`:
- computes `declinedSince = now - windowHours`
- keeps items with `declinedAt >= declinedSince`
- keeps previous category/forceRequired/query filters unchanged

### 3) DTO / CSV / audit alignment

Added `windowHoursFilter` to:
- `PreviewQueueDeclinedSummaryDto`
- `PreviewQueueDeclinedRequeueResponseDto`
- `PreviewQueueDeclinedRequeueDryRunResponseDto`
- `PreviewQueueDeclinedClearResponseDto`

Declined CSV:
- header now includes `windowHoursFilter`
- each row includes the resolved filter value

Audit events updated:
- `PREVIEW_QUEUE_DECLINED_EXPORTED`
- `PREVIEW_QUEUE_DECLINED_REQUEUE`
- `PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN`
- `PREVIEW_QUEUE_DECLINED_CLEAR`

All now include `windowHours=...`.

## Frontend changes

Files:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

### 1) Service contract and API propagation

- declined API methods now accept and send `windowHours` query param:
  - `getQueueDeclinedSummary`
  - `exportQueueDeclinedCsv`
  - `requeueQueueDeclined`
  - `dryRunQueueDeclinedRequeue`
  - `clearQueueDeclined`
- added normalization helper to omit invalid/`ANY` values.
- response types aligned to backend field:
  - `windowHoursFilter` (plus optional compatibility field).

### 2) Preview Diagnostics UI

In `Preview Queue Declined` panel:
- added `windowHours` dropdown:
  - `Any`
  - `1h`
  - `6h`
  - `24h`
  - `7d`
- wired filter into all declined API actions (refresh/export/requeue/dry-run/clear).
- clear filters resets `windowHours` back to `Any`.
- filter chip now shows resolved window label.
- dry-run summary now includes `windowHours`.

### 3) Mocked e2e updates

- mock declined routes parse `windowHours` query param.
- filtering logic applies time-window on `declinedAt`.
- response payloads return `windowHoursFilter`.
- CSV header/body includes `windowHoursFilter`.
- assertions validate `windowHours` propagation for summary/export/requeue/dry-run/clear.

## Tests updated

File:
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Added/updated assertions:
- endpoints accept `windowHours` parameter;
- responses echo `windowHoursFilter`;
- old declined records are filtered out under constrained window;
- CSV includes `windowHoursFilter` column;
- audit messages include `windowHours`.
