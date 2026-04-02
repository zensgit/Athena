# Phase 188 - Ops Recovery History Compare Breakdown By Mode (Development)

## Date
2026-03-07

## Goal
- Add per-event/per-mode compare breakdown between current window and previous window.
- Surface mode-level deltas directly in Preview Diagnostics Ops Recovery History panel.
- Keep event type filters aligned with all recovery export audit events.

## Implemented

### 1) Backend compare breakdown API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/summary/compare/breakdown`
  - Added compare breakdown logic:
    - `queryHistorySummaryCompareBreakdown(days, mode, actor, eventType)`
    - current-window counts derived from summary aggregation
    - previous-window grouped counts scanned in `[now-2*days, now-days)` with same filters
  - Added helpers:
    - `countHistoryEntriesByEventTypeInRange(...)`
    - `toRecoveryMode(eventType)`
  - Added DTOs:
    - `RecoveryHistorySummaryCompareBreakdownResponseDto`
    - `RecoveryHistorySummaryCompareBreakdownItemDto`
      - `eventType`, `mode`, `currentCount`, `previousCount`, `delta`, `deltaPercent`

### 2) Backend security/contract tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for:
    - `/api/v1/ops/recovery/history/summary/compare/breakdown`
  - Added admin success case:
    - validates compare breakdown payload and delta fields.

### 3) Frontend service + UI integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added types:
    - `RecoveryHistorySummaryCompareBreakdownResult`
    - `RecoveryHistorySummaryCompareBreakdownItem`
  - Added API:
    - `getHistorySummaryCompareBreakdown(days, mode, actor, eventType)`
  - Expanded `RecoveryHistoryEventType` union:
    - `OPS_RECOVERY_HISTORY_TREND_EXPORT`
    - `OPS_RECOVERY_HISTORY_COMPARE_EXPORT`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added breakdown state + load path in `Promise.all`.
  - Added mode delta chips:
    - format `Δ <MODE> +/-<delta>`
    - tooltip shows current/previous counts and delta percent.
  - Added missing event-type filter options for trend export and compare export.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added mocked route:
    - `/api/v1/ops/recovery/history/summary/compare/breakdown`
  - Added UI assertion:
    - `Δ DRY_RUN +1`
  - Added request propagation assertions (`days/mode/actor/eventType`).
