# Phase 174 - Ops Recovery Execution History Audit Timeline (Development)

## Date
2026-03-07

## Goal
- Give admins a direct, in-page timeline of recent recovery actions (queue/replay/dry-run), so operation loops become inspectable and auditable without jumping to generic audit pages.

## Implemented

### 1) Backend history query API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added `GET /api/v1/ops/recovery/history?limit=20&days=7`.
  - Reads recent `OPS_RECOVERY_*` audit events and returns:
    - `domain`
    - `windowDays`
    - `limit`
    - `total`
    - `items[]` (`id/eventType/mode/actor/eventTime/details`)
  - Added DTOs:
    - `RecoveryHistoryResponseDto`
    - `RecoveryHistoryItemDto`

### 2) Dry-run now participates in audit timeline
- Updated `OpsRecoveryController`:
  - Added `auditDryRun(...)`.
  - Every successful dry-run now records `OPS_RECOVERY_DRY_RUN` with scoped details (`mode/force/reason/category/retryable/window/estimates`).

### 3) Audit repository support
- Updated `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`:
  - Added prefix-based query methods for recovery event families:
    - `findByEventTypePrefix(...)`
    - `findByEventTypePrefixSince(...)`

### 4) Frontend service + diagnostics page
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added history contracts:
    - `RecoveryHistoryItem`
    - `RecoveryHistoryResult`
  - Added `getHistory(limit, days)` API client.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Loads ops recovery history in existing diagnostics refresh flow.
  - Added `Ops Recovery Execution History` panel with:
    - current window chip;
    - event count chip;
    - refresh action;
    - timeline table (`Time/Mode/Actor/Event Type/Details`).

### 5) Mock E2E coverage update
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added mock route for `GET /api/v1/ops/recovery/history`.
  - Added assertions for history panel rendering and event details.
  - Added call assertions to ensure history is requested for both `7` and `30` day windows.

### 6) Security test expansion
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for `/api/v1/ops/recovery/history` when user is non-admin.
  - Added admin success test for history response payload.
