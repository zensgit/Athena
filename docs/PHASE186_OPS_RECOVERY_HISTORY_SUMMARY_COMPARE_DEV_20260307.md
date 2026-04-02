# Phase 186 - Ops Recovery History Summary Compare KPIs (Development)

## Date
2026-03-07

## Goal
- Add current-window vs previous-window KPI comparison for ops recovery history.
- Keep comparison filters aligned with summary/trend/list (`days/mode/actor/eventType`).

## Implemented

### 1) Backend compare endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/summary/compare`
  - Added compare logic:
    - `queryHistorySummaryCompare(days, mode, actor, eventType)`
    - current metrics from `queryHistorySummary(...)`
    - previous-window metrics by scanning `[now-2*days, now-days)` with same filters
  - Added helper:
    - `countHistoryEntriesInRange(...)`
    - bounded by existing trend scan guardrail
  - Added response DTO:
    - `RecoveryHistorySummaryCompareResponseDto`
      - `currentTotal`, `previousTotal`, `delta`, `deltaPercent`
      - `compareAvailable`
      - `truncated`
  - For `days=0` (all-time), compare is marked unavailable.

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for `/history/summary/compare`.
  - Added admin success test covering compare payload fields.

### 3) Frontend service + UI
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added compare types and API:
    - `RecoveryHistorySummaryCompareResult`
    - `getHistorySummaryCompare(days, mode, actor, eventType)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added compare state and load integration.
  - Added compare KPI chips:
    - `Compare current`
    - `Previous`
    - `Delta`
    - `Delta%`
    - optional `Compare truncated`
  - Shows fallback chip when compare unavailable.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added compare route mock:
    - `/api/v1/ops/recovery/history/summary/compare`
  - Added UI assertions for compare KPI chips.
  - Added request propagation assertions (`days/mode/actor/eventType`).
