# Phase 191 - Ops Recovery History Actor Compare Sort + TopN (Development)

## Date
2026-03-07

## Goal
- Add actor-dimension comparison between current and previous windows.
- Support server-side sort + TopN limiting for actor compare insights.
- Expose actor compare controls in diagnostics UI and validate request propagation end-to-end.

## Implemented

### 1) Backend actor-compare API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/summary/compare/actors`
  - Added actor compare logic:
    - `queryHistorySummaryCompareActors(days, mode, actor, eventType, limit, sort)`
    - current actor counts from summary actor aggregation
    - previous actor counts by scanning previous window range
  - Added helpers:
    - `countHistoryEntriesByActorInRange(...)`
    - `normalizeCompareActorLimit(...)`
    - `normalizeCompareActorSort(...)`
    - `sortAndLimitCompareActorItems(...)`
    - `compareActorComparator(...)`
  - Added response DTOs:
    - `RecoveryHistorySummaryCompareActorsResponseDto`
    - `RecoveryHistorySummaryCompareActorItemDto`
  - Supported actor compare sort values:
    - `DELTA_ABS_DESC`, `DELTA_DESC`, `DELTA_ASC`, `CURRENT_DESC`, `PREVIOUS_DESC`, `ACTOR_ASC`

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added admin-role protection check for `/history/summary/compare/actors`.
  - Added actor compare response test.
  - Added actor compare sort + TopN test (`DELTA_DESC`, `limit=1`).

### 3) Frontend service + page
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added types:
    - `RecoveryHistoryCompareActorSort`
    - `RecoveryHistorySummaryCompareActorsResult`
    - `RecoveryHistorySummaryCompareActorItem`
  - Added API:
    - `getHistorySummaryCompareActors(..., limit, sort)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added actor compare controls:
    - sort selector (`Ops recovery actor compare sort`)
    - TopN selector (`Ops recovery actor compare top`)
  - Added actor compare chips:
    - `Actor Δ <actor> +/-<delta>`
  - Added limited-result chip:
    - `Actor showing X/Y`
  - Wired actor compare load path into diagnostics refresh.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added actor compare mock route:
    - `/api/v1/ops/recovery/history/summary/compare/actors`
  - Added UI interactions for actor compare TopN/sort controls.
  - Added assertions for default + updated `limit/sort` propagation.
