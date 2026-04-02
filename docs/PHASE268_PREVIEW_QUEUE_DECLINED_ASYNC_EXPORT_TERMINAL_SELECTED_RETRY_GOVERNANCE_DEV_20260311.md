# Phase 268 - Preview Queue Declined Async Export Terminal Selected-Retry Governance (Dev)

Date: 2026-03-11  
Stream: Day2-7 Stream B (queue governance)

## Objective

Close the dry-run to execution loop by allowing operators to retry explicitly selected terminal async export tasks by source task id.

## Backend changes

Files:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

### Selected-retry endpoint

- Add `POST /api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/by-task-ids`.
- Request body:
  - `sourceTaskIds: string[]`
- Behavior:
  - normalize/trim/deduplicate `sourceTaskIds` (`max=200`);
  - retry terminal source tasks only;
  - skip `NOT_FOUND` and non-terminal tasks;
  - clone source filter snapshot (`limit/category/forceRequired/query/windowHours`) and create new async tasks.

### Response model

- Reuse bulk retry response contract:
  - `requested/retried/skipped/failed/limit/statusFilter/message/results[]`
  - `statusFilter` fixed to `BY_TASK_IDS`
  - `results[]` contains per-source `outcome` (`RETRIED/SKIPPED/FAILED`) and `newTaskId`.

### Audit

- Add selected bulk retry audit event:
  - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_SELECTED`
- Audit details include:
  - `requested/retried/skipped/failed`
  - `sourceTaskIds/newTaskIds`
  - merged queue-declined filter context.

### Security/admin tests

- Non-admin forbidden coverage includes selected-retry endpoint.
- Admin flow validates:
  - selected-retry returns new task id and retried metrics;
  - created task can be queried;
  - audit stream contains selected-retry event with governance counters.

## Frontend changes

Files:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

### Service API

- Add `retryTerminalQueueDeclinedExportTasksByTaskIds(sourceTaskIds)`:
  - `POST /preview/diagnostics/queue/declined/export-async/retry-terminal/by-task-ids`

### Preview diagnostics UI

- Dry-run result now forms a selectable candidate panel:
  - checkbox selection per `sourceTaskId`;
  - `Select All` / `Clear Selection`;
  - `Retry Selected` execution button.
- Execution result toast:
  - `Queue declined async selected retry done: retried=..., skipped=..., failed=...`

### Mocked e2e

- Add mock route for selected-retry endpoint.
- Validate end-to-end sequence:
  - dry-run terminal
  - retry selected candidates
  - retry terminal (existing path)
  - task center refresh and assertions.
