# Phase 267 - Preview Queue Declined Async Export Terminal Bulk Retry Dry-run Governance (Dev)

Date: 2026-03-11  
Stream: Day2-7 Stream B (queue governance)

## Objective

Add a dry-run control plane for terminal bulk retry so operators can preview retryable task scope before executing retry.

## Backend changes

Files:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

### Dry-run endpoint

- Add `POST /api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run`.
- Query params:
  - `status` (optional): `COMPLETED` / `CANCELLED` / `FAILED`
  - `limit` (optional): default `20`, range `1..200`
- Behavior:
  - reject active status filter (`QUEUED` / `RUNNING`) with `400`;
  - when `status` absent, default dry-run scope is terminal `FAILED + CANCELLED`;
  - list recent retry candidates without creating new tasks.

### Dry-run response DTO

- Add response fields:
  - `requested`
  - `retryable`
  - `skipped`
  - `limit`
  - `statusFilter`
  - `message`
  - `results[]` with `sourceTaskId/sourceStatus/outcome/message`

### Audit

- Add dry-run audit event:
  - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN`
- Audit details include:
  - `statusFilter`
  - `limit`
  - `requested/retryable/skipped`
  - `sourceTaskIds`
  - merged filter context for sampled tasks

### Security/admin tests

- Non-admin forbidden coverage includes dry-run endpoint.
- Admin flow validates:
  - dry-run returns `requested/retryable` metrics;
  - audit stream contains `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN`.

## Frontend changes

Files:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

### Service API

- Add `dryRunRetryTerminalQueueDeclinedExportTasks(status?, limit?)`:
  - `POST /preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run`
- Add typed response models for dry-run payload.

### Preview diagnostics UI

- Add bulk action button in queue declined async task center:
  - `Dry-run Terminal`
- Status propagation aligns with execution path:
  - `FAILED/CANCELLED/COMPLETED`: pass status;
  - `ALL` / `QUEUED` / `RUNNING`: do not pass status (backend default `FAILED|CANCELLED`).
- Toast reports dry-run summary using `retryable/skipped`.

### Mocked e2e

- Add mock route:
  - `POST /api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run`
- Validate:
  - request `status`/`limit` propagation;
  - dry-run result toast shown before execute retry;
  - call tracking/assertions cover dry-run and execute paths.
