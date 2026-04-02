# Phase 185 - Ops Recovery History Trend CSV Export (Development)

## Date
2026-03-07

## Goal
- Add export capability for daily recovery history trend.
- Keep filter semantics identical with summary/trend/list flows.

## Implemented

### 1) Backend trend export API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/summary/trend/export`
  - Reuses `queryHistoryTrend(days, mode, actor, eventType)`.
  - Added CSV builder:
    - `buildHistoryTrendCsv(items)`
    - columns: `day,count`
  - Added audit event:
    - `OPS_RECOVERY_HISTORY_TREND_EXPORT`
  - Added response header:
    - `X-Ops-Recovery-Trend-Count`.

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for trend export endpoint.
  - Added admin success-path test:
    - verifies CSV headers and body fields.

### 3) Frontend integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added `exportHistorySummaryTrendCsv(days, mode, actor, eventType)`.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added action handler and button:
    - `Export Trend CSV`
  - Added success/failure toast messages.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added trend export mock route:
    - `/api/v1/ops/recovery/history/summary/trend/export`
  - Added UI click + toast assertion.
  - Added filter propagation assertions for trend export request.
