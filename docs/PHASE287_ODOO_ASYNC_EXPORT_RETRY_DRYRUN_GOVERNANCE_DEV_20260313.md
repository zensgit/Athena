# Phase 287 - Odoo Async Export Retry Dry-run Governance (Dev)

## Date
- 2026-03-13

## Goal
- Extend Odoo-style async task governance in two admin task centers:
  - preview rendition resources async export center
  - ops recovery history async export center
- Add terminal-task retry **dry-run** and **dry-run CSV export** capabilities, with deterministic reason breakdown for operators.

## Odoo Benchmark Mapping
- Odoo job centers provide clear pre-execution visibility before retry/requeue operations.
- Athena Phase287 parity/surpass points:
  - dry-run simulation for terminal retries without mutating task state.
  - reason-code breakdown and CSV export for offline governance/approval.
  - frontend operator workflow for in-page dry-run review + one-click CSV export.

## Scope
- Backend
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
- Backend tests
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
- Frontend
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - `ecm-frontend/src/services/opsRecoveryService.ts`
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- Frontend mock e2e
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

## Backend Implementation

### Preview rendition resources async task center
- Added terminal retry dry-run APIs:
  - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run`
  - `GET /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run/export`
- Added dry-run computation and CSV builder:
  - `computeRenditionResourcesExportAsyncRetryTerminalDryRun(...)`
  - `buildRenditionResourcesExportAsyncRetryTerminalDryRunCsv(...)`
- Added structured response DTOs and reason breakdown DTO.
- Enforced terminal-status validation and dedup-aware dry-run reason coding.

### Ops recovery history async task center
- Added terminal retry dry-run APIs:
  - `POST /api/v1/ops/recovery/history/export-async/retry-terminal/dry-run`
  - `GET /api/v1/ops/recovery/history/export-async/retry-terminal/dry-run/export`
- Added dry-run computation and CSV builder:
  - `computeHistoryExportAsyncRetryTerminalDryRun(...)`
  - `buildHistoryExportAsyncRetryTerminalDryRunCsv(...)`
- Added structured response DTOs and reason breakdown DTO.
- Enforced terminal-status validation and dedup-aware dry-run reason coding.

## Frontend Implementation

### Service layer
- `previewDiagnosticsService`:
  - `dryRunRetryTerminalRenditionResourcesExportTasks(...)`
  - `exportDryRunRetryTerminalRenditionResourcesExportTasks(...)`
- `opsRecoveryService`:
  - `dryRunRetryTerminalHistoryExportAsyncTasks(...)`
  - `exportDryRunRetryTerminalHistoryExportAsyncTasks(...)`

### PreviewDiagnosticsPage integration
- Rendition async export panel:
  - added `Dry-run Terminal` and `Export Dry-run CSV` actions.
  - added dry-run candidate panel with requested/retryable/skipped chips, reason breakdown chips, and candidate table.
- Ops recovery async export panel:
  - added `Dry-run Terminal` and `Export Dry-run CSV` actions.
  - added dry-run candidate panel with requested/retryable/skipped chips, reason breakdown chips, and candidate table.
- Added state lifecycle cleanup: clear stale dry-run snapshot after real retry operations.

### Mock e2e parity update
- Extended `admin-preview-diagnostics.mock.spec.ts` so the mocked admin diagnostics journey now covers:
  - rendition async task-center terminal retry dry-run
  - rendition dry-run CSV export
  - rendition selected retry from dry-run candidates
  - ops recovery async task-center terminal retry dry-run
  - ops recovery dry-run CSV export
  - ops recovery selected retry from dry-run candidates
- Updated mocked async task list payloads for rendition + ops centers to return `paging` with `maxItems/skipCount/totalItems/hasMoreItems`, aligning the mocked contract with Phase286 standard paging.

## Test Additions
- Extended `diagnosticsRenditionResourcesAsyncRetryTerminalBulkForAdmin` to cover:
  - rendition retry-terminal dry-run API response contract.
  - rendition retry-terminal dry-run CSV export contract + header.
- Extended `historyExportAsyncRetryTerminalBulkForAdmin` to cover:
  - ops recovery retry-terminal dry-run API response contract.
  - ops recovery retry-terminal dry-run CSV export contract + header.

## Notes
- Fixed compile issue during integration by adding missing `java.util.HashMap` import in `OpsRecoveryController`.
