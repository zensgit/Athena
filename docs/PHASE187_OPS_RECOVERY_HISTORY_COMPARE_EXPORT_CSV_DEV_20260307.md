# Phase 187 - Ops Recovery History Compare CSV Export (Development)

## Date
2026-03-07

## Goal
- Add CSV export for current-vs-previous summary comparison KPIs.
- Keep compare export filters aligned with summary compare endpoint.

## Implemented

### 1) Backend compare export API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/summary/compare/export`
  - Reuses:
    - `queryHistorySummaryCompare(days, mode, actor, eventType)`
  - Added CSV builder:
    - `buildHistoryCompareCsv(compare)`
    - columns:
      - `domain,windowDays,previousWindowDays,modeFilter,actorFilter,eventTypeFilter,currentTotal,previousTotal,delta,deltaPercent,compareAvailable,truncated`
  - Added audit event:
    - `OPS_RECOVERY_HISTORY_COMPARE_EXPORT`
  - Added response header:
    - `X-Ops-Recovery-Compare-Count`.

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for compare export endpoint.
  - Added admin success test:
    - verifies content-type/header
    - verifies compare CSV fields.

### 3) Frontend integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added:
    - `exportHistorySummaryCompareCsv(days, mode, actor, eventType)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added action button:
    - `Export Compare CSV`
  - Added handler + toast messages:
    - success: `Ops recovery history compare CSV exported`
    - failure: `Failed to export ops recovery history compare CSV`.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added compare export route mock:
    - `/api/v1/ops/recovery/history/summary/compare/export`
  - Added UI click + toast assertion.
  - Added request propagation assertions for `days/mode/actor/eventType`.
