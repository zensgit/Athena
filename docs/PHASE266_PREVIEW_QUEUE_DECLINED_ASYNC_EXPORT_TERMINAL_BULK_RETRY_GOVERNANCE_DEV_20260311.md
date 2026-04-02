# Phase 266 - Preview Queue Declined Async Export Terminal Bulk Retry Governance (Dev)

Date: 2026-03-11  
Stream: Day2-7 Stream B (queue governance)

## Objective

Add bulk retry governance for terminal declined async export tasks so operators can recover multiple failed/cancelled tasks in one action.

## Backend changes

Files:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

### Bulk retry endpoint

- Add `POST /api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal`.
- Query params:
  - `status` (optional): `COMPLETED` / `CANCELLED` / `FAILED`
  - `limit` (optional): default `20`, range `1..200`
- Behavior:
  - reject active status filter (`QUEUED` / `RUNNING`) with `400`;
  - when `status` absent, default retry scope is terminal `FAILED + CANCELLED`;
  - select recent terminal tasks, clone task filter snapshot (`limit/category/forceRequired/query/windowHours`), create new async tasks, and run asynchronously.

### Bulk retry response DTO

- Add response fields:
  - `requested`
  - `retried`
  - `skipped`
  - `failed`
  - `limit`
  - `statusFilter`
  - `message`
  - `results[]` with `sourceTaskId/newTaskId/sourceStatus/outcome/message`

### Audit

- Add bulk retry audit event:
  - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK`
- Audit details include:
  - `statusFilter`
  - `limit`
  - `requested/retried/skipped/failed`
  - `sourceTaskIds/newTaskIds`
  - merged filter context for involved tasks

### Security/admin tests

- Non-admin forbidden coverage includes new endpoint.
- Admin flow validates:
  - bulk retry returns retried tasks and new task id;
  - new task can be queried;
  - audit stream includes `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK` with governance context.

## Frontend changes

Files:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

### Service API

- Add `retryTerminalQueueDeclinedExportTasks(status?, limit?)`:
  - `POST /preview/diagnostics/queue/declined/export-async/retry-terminal`
- Add typed response models for bulk retry result payload.

### Preview diagnostics UI

- Add bulk action button in queue declined async task center:
  - `Retry Terminal`
- Status propagation:
  - `FAILED/CANCELLED/COMPLETED`: pass status;
  - `ALL`: do not pass status (backend default `FAILED|CANCELLED`);
  - `QUEUED/RUNNING`: do not pass status (avoid invalid active filter).
- Fixed bulk retry limit to `20` and aligned declined async task list limit to `20`.
- Toast now reports retry summary with `retried/skipped/failed`.

### Mocked e2e

- Add mock route:
  - `POST /api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal`
- Validate:
  - request `status`/`limit` propagation;
  - bulk retried task creation;
  - task appears in subsequent list flow (all-status refresh);
  - call tracking/assertions cover new bulk retry action.
