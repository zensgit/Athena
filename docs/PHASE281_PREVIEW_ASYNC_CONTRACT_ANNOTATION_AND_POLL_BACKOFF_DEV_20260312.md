# Phase 281 - Preview Async Contract Annotation and Poll Backoff (Dev)

## Date
- 2026-03-12

## Goal
- Complete async contract discoverability and runtime operability after Phase280:
  - OpenAPI annotations for `202 + Location` on queue-declined async create/retry endpoints.
  - Frontend task-center auto refresh with backoff to reduce polling pressure while preserving status timeliness.

## Alfresco Benchmark Mapping
- Reference:
  - `remote-api` async patterns in Downloads/NodeSizeDetails (`202 + id + polling`)
  - RM bulk status polling relation resources.
- Athena extension:
  - stronger explicit API contract via OpenAPI `Location` response header annotation.
  - UI-side adaptive polling backoff for async task-center stabilization.

## Scope
- Backend:
  - OpenAPI response contract annotations for queue-declined async entry points.
- Frontend:
  - queue-declined non-requeue task-center auto poll with exponential backoff.
  - queue-declined requeue dry-run task-center auto poll with exponential backoff.

## Implementation
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added imports: `@ApiResponses`, `@ApiResponse`, `@Header`, `@Schema`.
  - Added `202` response annotations (with `Location` header schema) to:
    - `POST /queue/declined/export-async`
    - `POST /queue/declined/export-async/{taskId}/retry`
    - `POST /queue/declined/export-async/retry-terminal`
    - `POST /queue/declined/export-async/retry-terminal/by-task-ids`
    - `POST /queue/declined/requeue/dry-run/export-async`
    - `POST /queue/declined/requeue/dry-run/export-async/{taskId}/retry`
    - `POST /queue/declined/requeue/dry-run/export-async/retry-terminal`
    - `POST /queue/declined/requeue/dry-run/export-async/retry-terminal/by-task-ids`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added adaptive polling constants:
    - `ASYNC_TASK_POLL_BASE_MS = 2000`
    - `ASYNC_TASK_POLL_MAX_MS = 15000`
  - Added polling delay refs for both queue-declined task centers.
  - Added active-task detection (`QUEUED/RUNNING` or summary active count).
  - Added `setTimeout`-based polling effects with backoff growth (`*1.5`, capped at 15s).
  - Auto reset backoff to base interval when no active tasks.

## Expected Outcomes
- API consumers can discover `202 + Location` behavior directly from Swagger/OpenAPI.
- Async task-center UX remains fresh while reducing unnecessary fixed-interval polling load.
