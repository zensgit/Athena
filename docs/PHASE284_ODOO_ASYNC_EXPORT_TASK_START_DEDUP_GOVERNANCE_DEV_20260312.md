# Phase 284 - Odoo Async Export Start Dedup Governance (Dev)

## Date
- 2026-03-12

## Goal
- Extend Phase283 retry dedup governance to async export **start** behavior for:
  - preview rendition resources async export center
  - ops recovery history async export center
- Prevent duplicate active tasks when operators repeatedly trigger the same export request.
- Align async contract semantics with queue-governance standards by using `202 Accepted + Location` for start/retry endpoints in both centers.

## Odoo Benchmark Mapping
- Odoo-style long-running jobs favor idempotent start semantics for identical active work.
- Athena Phase284 parity/surpass points:
  - same-request/snapshot start requests now reuse active `QUEUED/RUNNING` task ids.
  - response returns structured dedup metadata (`deduplicated`, `deduplicatedFromTaskId`, `message`).
  - frontend start UX distinguishes `started` vs `reused`, reducing operator confusion and task amplification.

## Scope
- Backend:
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
- Frontend:
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Implementation
- Backend start dedup behavior:
  - `POST /api/v1/preview/diagnostics/renditions/resources/export-async`
  - `POST /api/v1/ops/recovery/history/export-async`
  - both endpoints now:
    - refresh lifecycle state before admission,
    - look up equivalent active task by normalized request snapshot,
    - reuse task when matched (`deduplicated=true`),
    - create and run new task only on dedup miss.
- Existing create response contracts are reused and now consistently populated for both start and retry paths.
- Async contract alignment:
  - `POST /api/v1/preview/diagnostics/renditions/resources/export-async`
  - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/retry`
  - `POST /api/v1/ops/recovery/history/export-async`
  - `POST /api/v1/ops/recovery/history/export-async/{taskId}/retry`
  - all four endpoints now return `202 Accepted` with `Location` header pointing to task status endpoint.
- Frontend integration:
  - start action now shows `info` toast when dedup hit is returned,
  - keeps success toast for real task creation.

## Compatibility
- Backward compatible:
  - no endpoint removal/rename;
  - response fields already optional in frontend typing;
  - no change to cancel/download/list/summary contracts.
