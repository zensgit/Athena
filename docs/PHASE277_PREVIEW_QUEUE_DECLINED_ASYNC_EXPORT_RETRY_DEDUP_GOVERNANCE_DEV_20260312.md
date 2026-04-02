# Phase 277 - Preview Queue Declined Async Export Retry Dedup Governance (Dev)

## Date
- 2026-03-12

## Goal
- Complete dedup governance symmetry for `queue/declined/export-async` retry flow:
  - single retry dedup,
  - bulk terminal retry dedup,
  - selected terminal retry dedup.

## Alfresco Benchmark Mapping
- Reference:
  - `remote-api/.../impl/DownloadsImpl.java`
  - `repository/.../batch/BatchProcessor.java`
- Borrowed ideas:
  - avoid duplicate work on repeat operator action (idempotent-like reuse),
  - keep long-running task execution observable while reducing duplicate active jobs.
- Athena extension:
  - expose dedup metadata (`deduplicated/deduplicatedFromTaskId`) and `REUSED` outcomes in retry governance payloads.

## Scope
- Backend:
  - `POST /queue/declined/export-async/{taskId}/retry`
  - `POST /queue/declined/export-async/retry-terminal`
  - `POST /queue/declined/export-async/retry-terminal/by-task-ids`
- Frontend:
  - queue declined async export row retry dedup feedback.
- Mocked e2e:
  - retry first-call retried + second-call reused branch.

## Design
- Build normalized retry request from source task snapshot.
- Check active-task dedup key via existing `findActiveQueueDeclinedExportAsyncTask(...)`.
- Dedup hit:
  - single retry returns existing active task with dedup metadata.
  - bulk/selected retry emit result `outcome=REUSED`, no new async job spawn.
- Dedup miss:
  - keep current retry task creation behavior.

## Implementation
- Backend controller:
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added dedup reuse on:
    - `POST /queue/declined/export-async/{taskId}/retry`
    - `POST /queue/declined/export-async/retry-terminal`
    - `POST /queue/declined/export-async/retry-terminal/by-task-ids`
  - Bulk/selected responses now include `REUSED` item outcomes and message-level `reused` count.
- Backend security test alignment:
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - Extended assertions for dedup hit and `REUSED` outcomes in single/bulk/selected retry flows.
  - Switched late-phase mock stubbing to `doAnswer/doReturn(...).when(...)` to avoid stale `thenThrow` chain interference during governance-path verification.
- Frontend UX:
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Row retry shows reuse toast when API returns `deduplicated=true`.
- Mocked e2e:
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added non-requeue retry dedup route simulation and reused-flow assertions.

## Expected Outcomes
- Reduced duplicate active retry tasks.
- Deterministic operator UX under repeated retry clicks.
- Backward-compatible API schema with additive `REUSED` semantics in item outcome.
