# Phase 177 - Ops Recovery History Pagination (Development)

## Date
2026-03-07

## Goal
- Improve operator navigation efficiency when recovery history grows.
- Keep list API and UI aligned on explicit page semantics while preserving existing filters.

## Implemented

### 1) Backend: page-aware history API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - `GET /api/v1/ops/recovery/history` now supports optional `page` (default `0`).
  - `queryHistoryPage(...)` now accepts page index and passes `PageRequest.of(page, limit)`.
  - Response payload `RecoveryHistoryResponseDto` extended with:
    - `page`
    - `totalPages`
  - Existing filters (`days`, `mode`, `limit`) remain unchanged.
- Export path remains first-page full snapshot by design:
  - `GET /api/v1/ops/recovery/history/export` continues to export from `page=0` with export limit.

### 2) Backend tests: pagination behavior coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Existing history tests now assert `page`/`totalPages`.
  - Added `listHistoryPaginationForAdmin`:
    - verifies response pagination metadata (`limit=2`, `page=1`, `totalPages=3`),
    - verifies repository receives matching pageable.

### 3) Frontend: paged history controls
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - `getHistory(limit, days, mode?, page=0)` now sends `page`.
  - `RecoveryHistoryResult` now includes `page` and `totalPages`.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added history state:
    - `recoveryHistoryPage`
    - `recoveryHistoryTotal`
    - `recoveryHistoryTotalPages`
  - Added history panel controls:
    - `Prev page`
    - `Next page`
    - page chip `Page X/Y`
  - Event chip now shows current-page/total counts (`Events current/total`).
  - Filter changes (`windowDays`, `recoveryHistoryMode`) reset page to `0`.

### 4) Mock E2E update
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - history route mock now parses and records `page` + `limit`.
  - route response now returns paged items and `totalPages`.
  - assertions include history page call and export limit validation.
