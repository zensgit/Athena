# Phase 283 - Odoo Async Export Terminal Retry Governance (Dev)

## Date
- 2026-03-12

## Goal
- Continue Phase282 lifecycle governance by adding terminal-task retry for:
  - preview rendition resources async export center
  - ops recovery history async export center
- Keep request context stable across retries by replaying stored task snapshots.

## Odoo Benchmark Mapping
- Odoo-style long-running job operations require explicit terminal re-run capability.
- Athena Phase283 parity/surpass points:
  - Terminal retry is explicit and guarded (`FAILED/CANCELLED/TIMED_OUT/EXPIRED` only).
  - Retry uses persisted normalized request snapshot to avoid operator drift.
  - UI task centers expose direct Retry with source/new task traceability.

## Scope
- Backend:
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
- Frontend:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - `ecm-frontend/src/services/opsRecoveryService.ts`
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Implementation
- Backend retry endpoints:
  - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/retry`
  - `POST /api/v1/ops/recovery/history/export-async/{taskId}/retry`
- Retry governance behavior:
  - `404` when source task is not found.
  - `409` when source task is non-terminal.
  - For terminal source, create a new task from stored snapshot and run async execution.
- Snapshot persistence:
  - Preview rendition async center stores normalized request per task id.
  - Ops recovery history async center stores snapshot per task id.
  - Snapshot map entries are cleaned alongside task cleanup/retention trimming.
- Frontend integration:
  - Service layer adds retry APIs for both centers.
  - Preview Diagnostics task tables add row-level `Retry` action for terminal retryable states.
  - Success toast reports `source task -> new task` and refreshes tasks/summary.

## Compatibility
- All additions are backward-compatible.
- Existing start/list/summary/cancel/download contracts are unchanged.
- Retry endpoints are additive.
