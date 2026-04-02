# Phase 190 - Ops Recovery History Compare Breakdown Sort + TopN (Development)

## Date
2026-03-07

## Goal
- Add server-side sorting and TopN limiting for compare-breakdown analytics.
- Keep compare-breakdown export aligned with runtime sort/limit filters.
- Expose sort/TopN controls in the diagnostics UI and verify end-to-end propagation.

## Implemented

### 1) Backend sort + TopN controls
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added query params to compare-breakdown APIs:
    - `limit` (default `10`, max `100`)
    - `sort` (default `DELTA_ABS_DESC`)
  - Supported sort values:
    - `DELTA_ABS_DESC`, `DELTA_DESC`, `DELTA_ASC`, `CURRENT_DESC`, `PREVIOUS_DESC`, `EVENT_TYPE_ASC`
  - Added helpers:
    - `normalizeCompareBreakdownLimit(...)`
    - `normalizeCompareBreakdownSort(...)`
    - `sortAndLimitCompareBreakdownItems(...)`
    - `compareBreakdownComparator(...)`
  - Extended compare-breakdown response fields:
    - `sortBy`, `requestedLimit`, `totalItems`, `limited`
  - Extended compare-breakdown CSV schema to include sort/limit metadata.

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Enhanced compare-breakdown assertion to verify sort/limit metadata.
  - Added behavior test:
    - sort + limit (`DELTA_DESC`, `limit=1`) returns expected top row.
  - Updated compare-breakdown export test:
    - verifies custom `sort`/`limit` are reflected in CSV output.

### 3) Frontend service integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added type:
    - `RecoveryHistoryCompareBreakdownSort`
  - Added optional response metadata:
    - `sortBy`, `requestedLimit`, `totalItems`, `limited`
  - Updated APIs:
    - `getHistorySummaryCompareBreakdown(..., limit, sort)`
    - `exportHistorySummaryCompareBreakdownCsv(..., limit, sort)`

### 4) Frontend page controls
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added compare-breakdown controls:
    - sort selector (`Ops recovery compare breakdown sort`)
    - TopN selector (`Ops recovery compare breakdown top`)
  - Added status chips:
    - `Top N`
    - `Showing X/Y` when server-side limiting applied
  - Wired load/export flows to pass selected sort + limit.

### 5) Mock E2E updates
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Compare-breakdown and export mocks now parse `limit`/`sort`.
  - Added UI interaction checks for changing TopN and sort.
  - Added assertions that API/export calls include default and updated `limit`/`sort`.
