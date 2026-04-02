# Phase 264 - Preview Queue Declined Async Export Status Filter Governance (Dev)

Date: 2026-03-11  
Stream: Day2-7 Stream B (queue governance)

## Objective

Enhance the declined async export task center with status-aware governance so operators can:

1. filter tasks by lifecycle status in UI;
2. run list/summary/cleanup/cancel-active under the selected status scope;
3. keep backend audit trail complete for async task operations.

## Backend changes

Files:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

### Async export audit coverage

Added queue declined async export audit events:

- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_STARTED`
- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCEL_SINGLE`
- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCEL_ACTIVE`
- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CLEANUP`
- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_DOWNLOADED`
- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_COMPLETED`
- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_FAILED`
- `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCELLED`

Event payload context includes:
- `taskId`
- `status`
- effective filter snapshot (`limit/category/forceRequired/query/windowHours`)
- action details (for example `deleted/remaining`, `cancelled/remainingActive`, exported count, filename/bytes, error/reason)

Implementation details:
- start request normalized first, then stored into task snapshot before async launch;
- internal `QueueDeclinedExportAsyncTask` now carries filter snapshot fields for later lifecycle/audit actions;
- terminal transitions inside async runner now emit completed/failed/cancelled audit events.

### Security/controller test additions

- Extended admin-path queue declined async flow to assert audit events via `ArgumentCaptor`:
  - start -> completed and failed terminal cases;
  - single cancel;
  - cancel-active;
  - cleanup;
  - download.

## Frontend changes

Files:
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

### Queue declined async status filter UI

- Add status filter dropdown:
  - `ALL`
  - `QUEUED`
  - `RUNNING`
  - `COMPLETED`
  - `CANCELLED`
  - `FAILED`
- Wire filter into:
  - task list API
  - task summary API
  - cleanup API (status-scoped cleanup)
  - cancel-active API (active-only status forwarding)

Behavior rules:
- list/summary use selected status (`ALL` -> `undefined`);
- cleanup uses selected status (`ALL` -> `undefined`);
- cancel-active forwards status only for active states (`QUEUED/RUNNING`), other selections degrade to active-all (`undefined`).

### Mocked e2e updates

- Add status-filter interaction and assertions that status query param propagates to:
  - `list`
  - `summary`
  - `cancel-active`
  - `cleanup`
- Covered terminal status scenarios:
  - `COMPLETED`
  - `CANCELLED`
