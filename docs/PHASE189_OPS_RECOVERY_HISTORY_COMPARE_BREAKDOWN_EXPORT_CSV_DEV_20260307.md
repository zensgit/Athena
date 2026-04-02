# Phase 189 - Ops Recovery History Compare Breakdown CSV Export (Development)

## Date
2026-03-07

## Goal
- Complete compare-breakdown workflow with CSV export.
- Keep export behavior and filters consistent with compare/summary/trend/history exports.
- Surface export action in the diagnostics UI for admin operations.

## Implemented

### 1) Backend compare-breakdown export API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/summary/compare/breakdown/export`
  - Reuses:
    - `queryHistorySummaryCompareBreakdown(days, mode, actor, eventType)`
  - Added CSV builder:
    - `buildHistoryCompareBreakdownCsv(compareBreakdown)`
  - Added response header:
    - `X-Ops-Recovery-Compare-Breakdown-Count`
  - Added audit event:
    - `OPS_RECOVERY_HISTORY_COMPARE_BREAKDOWN_EXPORT`
    - helper: `auditHistoryCompareBreakdownExport(...)`

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for compare-breakdown export endpoint.
  - Added admin export success case:
    - validates CSV content-type
    - validates `X-Ops-Recovery-Compare-Breakdown-Count`
    - validates CSV includes compare breakdown values.

### 3) Frontend integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added API:
    - `exportHistorySummaryCompareBreakdownCsv(days, mode, actor, eventType)`
  - Extended event type union:
    - `OPS_RECOVERY_HISTORY_COMPARE_BREAKDOWN_EXPORT`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added button:
    - `Export Compare Breakdown CSV`
  - Added handler + toasts:
    - success: `Ops recovery history compare breakdown CSV exported`
    - failure: `Failed to export ops recovery history compare breakdown CSV`
  - Added event type option:
    - `History Compare Breakdown Export Event`

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added route mock:
    - `/api/v1/ops/recovery/history/summary/compare/breakdown/export`
  - Added UI click + toast assertion for new export button.
  - Added request propagation assertions (`days/mode/actor/eventType`).
