# Phase 184 - Ops Recovery History Summary Trend by Day (Development)

## Date
2026-03-07

## Goal
- Add daily trend visibility for ops recovery history summary.
- Reuse existing filters (`days/mode/actor/eventType`) so trend and summary stay consistent.

## Implemented

### 1) Backend trend endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added `GET /api/v1/ops/recovery/history/summary/trend`.
  - Added `queryHistoryTrend(days, mode, actor, eventType)`:
    - normalizes filters with existing history logic
    - scans paged audit history and groups by `eventTime.toLocalDate()`
    - returns descending-by-day trend items
  - Added DTOs:
    - `RecoveryHistoryTrendResponseDto`
    - `RecoveryHistoryTrendItemDto`
  - Added scan guardrails:
    - `HISTORY_TREND_PAGE_LIMIT=500`
    - `MAX_HISTORY_TREND_SCAN=20000`
    - response includes `truncated` flag when scan cap is hit.

### 2) Backend security/controller tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for `/api/v1/ops/recovery/history/summary/trend`.
  - Added admin success test for trend endpoint:
    - verifies `mode/actor` filter propagation
    - verifies grouped day output and total count
    - verifies `truncated=false` in normal range.

### 3) Frontend service and diagnostics UI
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added types:
    - `RecoveryHistoryTrendItem`
    - `RecoveryHistoryTrendResult`
  - Added API method:
    - `getHistorySummaryTrend(days, mode, actor, eventType)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added trend state:
    - `recoveryHistoryTrend`
    - `recoveryHistoryTrendTotal`
    - `recoveryHistoryTrendTruncated`
  - Extended diagnostics load pipeline to fetch trend with same filters.
  - Added trend chips row in ops recovery history panel:
    - `Trend total <n>`
    - optional `Trend truncated`
    - per-day chips `Trend <YYYY-MM-DD> <count>`.

### 4) Mocked E2E coverage
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added mock route:
    - `/api/v1/ops/recovery/history/summary/trend`
  - Added request capture assertions for trend filter propagation.
  - Added UI assertion:
    - `Trend total 2`.
