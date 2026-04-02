# Phase 275 - Preview Queue Declined Requeue Dry-run Async Export Terminal Retry Governance (Dev)

## Date
- 2026-03-12

## Goal
- Complete the terminal-retry governance loop for `queue/declined/requeue/dry-run/export-async` so operators can:
  - retry one terminal async task,
  - bulk retry terminal tasks,
  - dry-run terminal retry candidates,
  - export dry-run diagnostics CSV,
  - retry selected terminal tasks by ids.

## Backend Design and Implementation

### New endpoints
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}/retry`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run`
- `GET /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run/export`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/by-task-ids`

### Governance rules
- `status` for terminal retry only accepts terminal states: `COMPLETED|CANCELLED|FAILED`.
- When `status` is absent, terminal retry defaults to `FAILED|CANCELLED`.
- Retry-selected request de-duplicates `sourceTaskIds` and enforces max size (`200`).
- Active source task (`QUEUED|RUNNING`) cannot be retried via single-task retry (`409`).

### Async retry dry-run model
- Added dry-run computation and CSV export model for requeue dry-run async tasks:
  - `requested/retryable/skipped`
  - `results[]` with `reasonCode/outcome/message`
  - `reasonBreakdown[]` aggregation

### Audit coverage
- Added audit event chain:
  - `PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY`
  - `..._RETRY_BULK`
  - `..._RETRY_BULK_DRY_RUN`
  - `..._RETRY_BULK_DRY_RUN_EXPORTED`
  - `..._RETRY_BULK_SELECTED`

## Frontend Design and Implementation

### Service layer
- Added requeue dry-run async retry APIs in `previewDiagnosticsService`:
  - single retry,
  - terminal retry,
  - terminal dry-run,
  - dry-run CSV export,
  - selected retry by task ids.
- Added matching TypeScript models for terminal retry response and dry-run response.

### UI (Preview Diagnostics)
- In `Requeue Dry-run Async Export Tasks` section:
  - added toolbar actions:
    - `Dry-run Terminal`
    - `Export Dry-run CSV`
    - `Retry Terminal`
  - added dry-run candidate table:
    - selectable `RETRYABLE` source tasks
    - `Retry Selected` action
  - added row-level `Retry` action for terminal rows (`FAILED/CANCELLED`).
- Terminal actions only forward terminal status filters; non-terminal UI filter resolves to `undefined`.

### Mocked e2e
- Extended mocked route logic for:
  - `/retry`
  - `/retry-terminal`
  - `/retry-terminal/dry-run`
  - `/retry-terminal/dry-run/export`
  - `/retry-terminal/by-task-ids`
- Extended UI flow assertions:
  - dry-run -> select -> retry selected -> retry terminal -> row retry.

## Changed Files
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
