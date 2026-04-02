# Phase 265 - Preview Queue Declined Async Export Retry Governance (Dev)

Date: 2026-03-11  
Stream: Day2-7 Stream B (queue governance)

## Objective

Add retry governance for declined async export tasks so operators can restart terminal tasks without re-entering filters manually.

## Backend changes

Files:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

### Retry endpoint

- Add `POST /api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/retry`.
- Behavior:
  - `404` when task not found;
  - `409` when source task is active (`QUEUED` / `RUNNING`);
  - create a new async export task when source task is terminal (`COMPLETED` / `CANCELLED` / `FAILED`).
- New task copies source task filter snapshot:
  - `limit`
  - `category`
  - `forceRequired`
  - `query`
  - `windowHours`

### Audit

- Add retry audit event:
  - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY`
- Audit message includes:
  - source task id
  - new task id
  - source status
  - effective filter snapshot

### Tests

- Extend security/admin controller tests:
  - non-admin retry forbidden;
  - admin retry flow works and returns new task id;
  - audit captor includes retry event.

## Frontend changes

Files:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

### Service

- Add `retryQueueDeclinedExportTask(taskId)` API method:
  - `POST /preview/diagnostics/queue/declined/export-async/{taskId}/retry`

### Preview diagnostics UI

- In queue declined async task table:
  - show `Retry` action for terminal retryable statuses (`FAILED` / `CANCELLED`);
  - reuse row action busy state for retry operation;
  - success toast includes new task id and refreshes task list.

### Mocked e2e

- Add retry route mock and call tracking.
- Extend scenario to execute one retry action and validate:
  - retry API called;
  - new retried task appears in list/flow.

