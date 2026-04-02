# Phase 263 - Preview Queue Declined Async Export Task Center (Dev)

Date: 2026-03-11  
Stream: Day2-7 Stream B (queue governance)

## Objective

Build a governed async export task center for Preview Queue Declined diagnostics so operators can:

1. start declined CSV export as background tasks;
2. observe task lifecycle (`QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`);
3. cancel active tasks, clean terminal tasks, and download completed CSV artifacts.

## Backend changes

File:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

### 1) Declined async export endpoints

Added endpoints:
- `POST /api/v1/preview/diagnostics/queue/declined/export-async`
- `GET /api/v1/preview/diagnostics/queue/declined/export-async`
- `GET /api/v1/preview/diagnostics/queue/declined/export-async/summary`
- `POST /api/v1/preview/diagnostics/queue/declined/export-async/cleanup`
- `POST /api/v1/preview/diagnostics/queue/declined/export-async/cancel-active`
- `GET /api/v1/preview/diagnostics/queue/declined/export-async/{taskId}`
- `POST /api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/cancel`
- `GET /api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/download`

Request payload (`start`):
- `limit`
- `category`
- `forceRequired`
- `query`
- `windowHours`

### 2) Async task state machine and bounded retention

Implemented in-memory task governance:
- task store: `ConcurrentHashMap + Deque + lock`
- max retained tasks: `MAX_QUEUE_DECLINED_EXPORT_ASYNC_TASKS`
- list cap: `MAX_QUEUE_DECLINED_EXPORT_ASYNC_LIST_LIMIT`
- terminal cleanup and status-filtered cleanup
- active cancellation (`QUEUED/RUNNING`) with optional status filter

### 3) Async execution path

Added async runner:
- snapshots request parameters at creation time;
- normalizes declined filters using existing helpers;
- reuses declined CSV builder for content generation;
- writes completion payload (`filename + csv bytes`) or failure message;
- supports cancellation checks before terminal transition.

### 4) DTOs / enums

Added records and enum:
- `PreviewQueueDeclinedExportAsyncRequestDto`
- `PreviewQueueDeclinedExportAsyncCreateResponseDto`
- `PreviewQueueDeclinedExportAsyncStatusResponseDto`
- `PreviewQueueDeclinedExportAsyncListResponseDto`
- `PreviewQueueDeclinedExportAsyncSummaryResponseDto`
- `PreviewQueueDeclinedExportAsyncCleanupResponseDto`
- `PreviewQueueDeclinedExportAsyncCancelActiveResponseDto`
- `QueueDeclinedExportAsyncStatus`
- `QueueDeclinedExportAsyncTask`

## Test changes

File:
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Coverage additions:
- non-admin `403` checks for all new declined async task endpoints;
- admin-path create/list/summary/get/cancel/download flow;
- active-task cancel/cleanup governance assertions.

## Frontend changes

Files:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

### 1) Service API contract

Added declined async export methods:
- `startQueueDeclinedExportTask(...)`
- `listQueueDeclinedExportTasks(...)`
- `getQueueDeclinedExportTaskSummary(...)`
- `cleanupQueueDeclinedExportTasks(...)`
- `cancelActiveQueueDeclinedExportTasks(...)`
- `getQueueDeclinedExportTask(taskId)`
- `cancelQueueDeclinedExportTask(taskId)`
- `downloadQueueDeclinedExportTask(taskId)`

Notable alignment:
- `startQueueDeclinedExportTask` now posts JSON body (not query-only), matching backend `@RequestBody`.

### 2) Preview Diagnostics task-center UI

In `Preview Queue Declined` section:
- new controls:
  - `Start Async Export`
  - `Refresh`
  - `Cancel Active`
  - `Cleanup`
- new async task summary chips:
  - total/active/queued/running/completed/cancelled/failed/terminal
- new task table:
  - task id, status, created/finished, filename
  - row actions: `Cancel` (active only), `Download` (completed only)
- task creation carries current declined filters:
  - `category`, `forceRequired`, `query`, `windowHours`, `limit`

### 3) Mocked e2e

Extended mock routes to cover:
- declined async export task create/list/summary/get/cancel/download/cleanup/cancel-active;
- request-body parsing for start API;
- lifecycle transition simulation (`QUEUED -> RUNNING -> COMPLETED`);
- filter propagation assertions for started tasks.

