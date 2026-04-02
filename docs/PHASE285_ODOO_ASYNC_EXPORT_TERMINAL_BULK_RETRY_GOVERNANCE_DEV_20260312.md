# Phase 285 - Odoo Async Export Terminal Bulk Retry Governance (Dev)

## Date
- 2026-03-12

## Goal
- Complete Odoo-oriented async task-center parity for terminal bulk retry in both centers:
  - preview rendition resources async export center
  - ops recovery history async export center
- Provide both governance modes:
  - retry terminal tasks by filter (`status` + optional `exportType`)
  - retry selected terminal tasks by `sourceTaskIds[]`
- Keep async contract aligned with prior phases (`202 Accepted + Location`), and surface structured metrics for operator decisions.

## Odoo Benchmark Mapping
- Odoo job-center workflows emphasize selective/bulk re-run for failed or terminal jobs.
- Athena Phase285 parity/surpass points:
  - backend supports both filtered bulk retry and selected-id retry across both async export centers.
  - retry paths reuse active equivalent snapshots (dedup) to prevent task amplification.
  - frontend task-center controls expose one-click terminal retry and visible-terminal selected retry.

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
- Preview rendition resources async export governance:
  - added bulk terminal retry endpoint:
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal`
  - added selected terminal retry endpoint:
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/by-task-ids`
  - response contract includes:
    - `requested/retried/reused/skipped/failed/limit/statusFilter/message/results[]`
  - selected-id normalization limits request fan-out and removes duplicates.
- Ops recovery history async export governance:
  - added bulk terminal retry endpoint:
    - `POST /api/v1/ops/recovery/history/export-async/retry-terminal`
  - added selected terminal retry endpoint:
    - `POST /api/v1/ops/recovery/history/export-async/retry-terminal/by-task-ids`
  - supports optional `exportType` and terminal-only `status` filter validation.
  - response contract includes:
    - `requested/retried/reused/skipped/failed/limit/exportTypeFilter/statusFilter/message/results[]`
  - selected-id normalization limits and deduplicates incoming source task ids.
- Frontend service integration:
  - preview service added rendition terminal retry types and APIs:
    - `retryTerminalRenditionResourcesExportTasks(...)`
    - `retryTerminalRenditionResourcesExportTasksByTaskIds(...)`
  - ops service added history terminal retry types and APIs:
    - `retryTerminalHistoryExportAsyncTasks(...)`
    - `retryTerminalHistoryExportAsyncTasksByTaskIds(...)`
- Frontend page integration:
  - rendition task-center controls:
    - `Retry terminal tasks` (filter-driven)
    - `Retry visible terminal` (selected by current visible terminal rows)
  - ops async task-center controls:
    - `Retry terminal tasks` (filter + optional exportType)
    - `Retry visible terminal` (selected by current visible terminal rows + optional exportType)
  - both centers render structured summary toasts:
    - `retried/reused/skipped/failed`

## Compatibility
- Backward compatible:
  - existing start/list/summary/retry/cancel/download contracts unchanged.
  - new endpoints are additive.
  - frontend existing row-level retry remains available.
