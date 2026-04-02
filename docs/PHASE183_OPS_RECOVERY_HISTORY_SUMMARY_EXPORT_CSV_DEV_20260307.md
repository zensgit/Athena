# Phase 183 - Ops Recovery History Summary CSV Export (Development)

## Date
2026-03-07

## Goal
- Add an export path for grouped recovery history summary.
- Keep summary export filters aligned with list/summary/history export semantics.

## Implemented

### 1) Backend summary export API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added/finished summary aggregation core path:
    - `queryHistorySummary(days, mode, actor, eventType)`
  - Added CSV endpoint:
    - `GET /api/v1/ops/recovery/history/summary/export`
  - Added summary CSV builder:
    - `buildHistorySummaryCsv(items, actorItems)`
    - output columns: `section,key,mode,count`
    - includes two sections:
      - `EVENT_TYPE` rows from grouped event summary
      - `ACTOR` rows from grouped actor summary
  - Added audit event for summary export:
    - `OPS_RECOVERY_HISTORY_SUMMARY_EXPORT`
    - details include `days/mode/actor/eventType/count`.

### 2) Backend test coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added admin-role forbidden check for:
    - `/api/v1/ops/recovery/history/summary/export`
  - Added success-path CSV export test:
    - verifies content-type/header and exported CSV payload sections.

### 3) Frontend service + page integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Extended `RecoveryHistoryEventType` with:
    - `OPS_RECOVERY_HISTORY_SUMMARY_EXPORT`
  - Added API method:
    - `exportHistorySummaryCsv(days, mode, actor, eventType)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added history event option:
    - `History Summary Export Event`
  - Added action button:
    - `Export Summary CSV`
  - Added toast flow for success/failure.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added route mock for:
    - `/api/v1/ops/recovery/history/summary/export`
  - Added verification for:
    - UI action `Export Summary CSV`
    - toast success text
    - propagated filters (`days/mode/actor/eventType`).
