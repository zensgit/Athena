# Phase 260 - Preview Queue Declined Requeue Dry-run (Dev)

Date: 2026-03-10  
Stream: Day2-7 Stream B (queue governance)

## Objective

On top of Phase259 declined governance, add a **non-mutating requeue dry-run** capability so operators can:

1. evaluate requeue outcomes before execution;
2. compare `force=true` vs `force=false`;
3. reduce unsafe or low-yield batch operations.

## Backend changes

### 1) Queue service dry-run decision API

File:
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

Added:
- `evaluateEnqueue(UUID documentId, boolean force)`  
  returns `PreviewQueueStatus` without mutating queue state.

Key behavior:
- mirrors enqueue gating checks:
  - queue disabled
  - prevention block
  - ready/up-to-date
  - unsupported
  - permanent failure requiring force
  - quiet period
  - already queued vs queueable decision
- no writes to queue structures or declined registry
- added stale-ledger-aware evaluation helpers:
  - `resolveEffectivePreviewStatusForEvaluation(...)`
  - `resolveEffectiveFailureReasonForEvaluation(...)`
  - `hasStaleFailureLedger(...)`

### 2) Diagnostics control-plane dry-run endpoint

File:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

Added endpoint:
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run`

Inputs:
- `limit`
- `category`
- `query`
- `force`

Output:
- requested count
- `estimatedQueued / estimatedSkipped / estimatedFailed`
- per-item decision details:
  - outcome
  - message
  - previewStatus
  - nextAttemptAt

Audit event:
- `PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN`

## Frontend changes

### 1) Service API bindings

File:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`

Added:
- `dryRunQueueDeclinedRequeue(...)`
- dry-run response/item types:
  - `PreviewQueueDeclinedRequeueDryRunResult`
  - `PreviewQueueDeclinedRequeueDryRunItem`

### 2) Preview Diagnostics panel enhancements

File:
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

In **Preview Queue Declined** panel:
- added `Force requeue` checkbox
- added `Dry-run Requeue` action button
- added dry-run summary alert with estimated outcomes
- requeue action now respects force toggle
- filter/force changes invalidate stale dry-run snapshot

## Tests updated

Backend:
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Frontend mocked e2e:
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

Coverage includes:
- admin/user access control for dry-run endpoint
- dry-run response correctness and audit emission
- UI flow:
  - force off -> dry-run -> estimated skip
  - force on -> execute requeue

