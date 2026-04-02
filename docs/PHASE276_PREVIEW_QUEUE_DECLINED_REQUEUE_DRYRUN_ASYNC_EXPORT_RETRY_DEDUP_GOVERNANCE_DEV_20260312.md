# Phase 276 - Preview Queue Declined Requeue Dry-run Async Export Retry Dedup Governance (Dev)

## Date
- 2026-03-12

## Goal
- Add active-task dedup governance to declined requeue dry-run async export retry operations so repeated retry clicks do not create duplicate active tasks.

## Scope
- Backend:
  - Single retry dedup (`/{taskId}/retry`).
  - Bulk terminal retry dedup (`/retry-terminal`).
  - Selected terminal retry dedup (`/retry-terminal/by-task-ids`).
- Frontend:
  - Surface dedup reuse feedback for single retry action.
- Regression:
  - Backend security/controller test coverage.
  - Frontend mocked e2e coverage.

## Backend Design
- Reuse the existing normalized request matching and active-task discovery:
  - `toQueueDeclinedRequeueDryRunExportAsyncRequest(sourceTask)`
  - `findActiveQueueDeclinedRequeueDryRunExportAsyncTask(normalizedRequest)`
- Dedup decision rule:
  1. Source task must be terminal.
  2. Build normalized retry request from source task snapshot.
  3. If an active task with the same normalized request exists, return/reuse that task id.
  4. Otherwise create a new retry task.

## API Behavior
- Single retry endpoint response:
  - On dedup hit: `deduplicated=true`, `deduplicatedFromTaskId=<activeTaskId>`.
  - On miss: existing behavior unchanged (`deduplicated=false`).
- Bulk/selected retry endpoints:
  - Keep response schema unchanged.
  - Allow item outcome `REUSED` when dedup hit occurs.
  - `retried` counter includes reused successes to preserve success semantics.

## Frontend UX
- Single retry action in Preview Diagnostics:
  - Dedup hit -> info toast: reused task id.
  - Dedup miss -> success toast: retried task id.

## Changed Files
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

