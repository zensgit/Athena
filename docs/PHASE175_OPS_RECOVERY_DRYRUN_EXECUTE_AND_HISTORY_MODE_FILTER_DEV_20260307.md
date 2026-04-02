# Phase 175 - Ops Recovery Dry-run Execute + History Mode Filter (Development)

## Date
2026-03-07

## Goal
- Close the last operator gap between dry-run and actual execution.
- Improve recovery timeline observability by supporting mode-level filtering.

## Implemented

### 1) Backend: recovery history mode filter
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - `GET /api/v1/ops/recovery/history` now supports optional `mode` query param.
  - Supported modes:
    - `QUEUE_BY_REASON`
    - `QUEUE_BY_WINDOW`
    - `REPLAY_BATCH`
    - `DRY_RUN`
  - Added `modeFilter` field in history response payload.
  - Mode filter uses exact event type (`OPS_RECOVERY_<MODE>`); without mode keeps prefix (`OPS_RECOVERY_`) query.
- Updated `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`:
  - Added Spring Data methods:
    - `findByEventTypeOrderByEventTimeDesc(...)`
    - `findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(...)`

### 2) Backend: security test extension
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added admin test for history mode-filter behavior (`mode=DRY_RUN`).
  - Kept non-admin forbidden coverage.

### 3) Frontend: execute recovery directly from dry-run panel
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added `Execute Recovery` action beside `Run Dry-run`.
  - Uses current dry-run criteria:
    - `QUEUE_BY_REASON` -> `opsRecoveryService.queueByReason(...)`
    - `QUEUE_BY_WINDOW` -> `opsRecoveryService.queueByWindow(...)`
  - Shows `queued/skipped/failed` outcome toasts and auto-refreshes diagnostics/history.

### 4) Frontend: recovery history mode filter UI
- Updated `PreviewDiagnosticsPage`:
  - Added `Ops recovery history mode` selector (`All/Queue by window/Queue by reason/Replay batch/Dry-run`).
  - Selector is wired to backend `mode` query and reflected in panel chip.
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added `RecoveryHistoryMode` type.
  - `getHistory(limit, days, mode?)` now supports mode filter.
  - Added `modeFilter` in history response type.

### 5) Mock E2E enhancement
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added `queue-by-window` route mock.
  - Updated dry-run mock to honor requested mode.
  - Updated recovery history mock to honor mode filter.
  - Added assertions for:
    - global dry-run then execute-recovery path;
    - history mode filter (`DRY_RUN`) and mode-specific timeline rendering.
