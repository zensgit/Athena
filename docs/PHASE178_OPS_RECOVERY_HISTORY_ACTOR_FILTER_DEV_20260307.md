# Phase 178 - Ops Recovery History Actor Filter (Development)

## Date
2026-03-07

## Goal
- Add actor-scoped history analysis for admin operations.
- Ensure list and CSV export share the same filtering semantics (`mode + actor + days`).

## Implemented

### 1) Backend: actor-aware history query path
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - `GET /api/v1/ops/recovery/history` now supports optional `actor`.
  - `GET /api/v1/ops/recovery/history/export` now supports optional `actor`.
  - `queryHistoryPage(...)` unified logic now branches by:
    - mode only
    - actor only
    - mode + actor
    - with/without window cutoff (`days`)
  - History response payload now includes `actorFilter`.
  - Export audit event `OPS_RECOVERY_HISTORY_EXPORT` now records `actor` in details.

### 2) Backend repository methods
- Updated `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`:
  - Added event-type + actor methods:
    - `findByEventTypeAndUsernameOrderByEventTimeDesc(...)`
    - `findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(...)`
  - Added prefix + actor methods:
    - `findByEventTypePrefixAndUsername(...)`
    - `findByEventTypePrefixSinceAndUsername(...)`

### 3) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added `listHistoryByActorForAdmin`.
  - Added `exportHistoryCsvByActorForAdmin`.
  - Existing security + paging + mode tests remain intact.

### 4) Frontend actor filter wiring
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - `getHistory(..., actor?)` and `exportHistoryCsv(..., actor?)`.
  - `RecoveryHistoryResult` extended with `actorFilter`.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added `Actor filter` input in Ops Recovery Execution History panel.
  - Added active actor chip.
  - List fetch and CSV export now pass actor filter.
  - Actor/mode/window change resets history page to `0` to prevent stale pagination.

### 5) Mock E2E enhancements
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - History route parses `actor`.
  - Response applies actor filtering before pagination.
  - Added assertions:
    - actor-filter request observed in history list calls,
    - actor propagated in CSV export request.
