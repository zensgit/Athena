# Phase 179 - Ops Recovery History EventType Filter (Development)

## Date
2026-03-07

## Goal
- Add precise event-level filtering for ops recovery history.
- Keep list/export query semantics aligned across mode/actor/eventType dimensions.

## Implemented

### 1) Backend: eventType filter for list + export
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - `GET /api/v1/ops/recovery/history` now accepts optional `eventType`.
  - `GET /api/v1/ops/recovery/history/export` now accepts optional `eventType`.
  - Added `normalizeHistoryEventType(...)`:
    - trims/uppercases input
    - only accepts values starting with `OPS_RECOVERY_`
  - `queryHistoryPage(...)` now handles precedence:
    - when `eventType` is present, use exact event type query path (with optional actor/time window)
    - otherwise fallback to existing mode/prefix logic.
  - Export audit details now include `eventType`.
  - History response DTO now includes `eventTypeFilter`.

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added `listHistoryByEventTypeForAdmin`.
  - Added `exportHistoryCsvByEventTypeForAdmin`.
  - Existing actor/mode/pagination coverage remains intact.

### 3) Frontend: eventType filter control
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added `RecoveryHistoryEventType` type.
  - `getHistory(..., actor?, eventType?)` and `exportHistoryCsv(..., actor?, eventType?)`.
  - `RecoveryHistoryResult` now includes optional `eventTypeFilter`.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added event-type selector (`All event types`, `Queue by Window Event`, `Queue by Reason Event`, `Replay Batch Event`, `Dry-run Event`, `History Export Event`).
  - Added active event-type chip.
  - List/export requests now include selected event type.
  - Event-type change resets history page to `0`.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - route parses `eventType` param for list/export.
  - list response applies eventType filtering before pagination.
  - assertions verify eventType is propagated in both history fetch and CSV export requests.
