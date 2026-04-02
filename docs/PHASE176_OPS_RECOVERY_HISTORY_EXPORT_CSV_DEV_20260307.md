# Phase 176 - Ops Recovery History CSV Export (Development)

## Date
2026-03-07

## Goal
- Provide a one-click export path for ops recovery execution history.
- Keep export behavior aligned with existing history window/mode filters for auditability.

## Implemented

### 1) Backend: history CSV export endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added `GET /api/v1/ops/recovery/history/export`.
  - Supports query params:
    - `limit` (default `500`, max `2000`)
    - `days` (same normalization as history list)
    - `mode` (`QUEUE_BY_REASON`, `QUEUE_BY_WINDOW`, `REPLAY_BATCH`, `DRY_RUN`)
  - Reuses history query pipeline to guarantee filter consistency with `GET /history`.
  - Returns CSV with headers:
    - `id,eventTime,eventType,mode,actor,details`
  - Response metadata:
    - `Content-Disposition: attachment; filename=ops_recovery_history_<timestamp>.csv`
    - `Content-Type: text/csv; charset=UTF-8`
    - `X-Ops-Recovery-Count: <exported rows>`
  - Adds audit event `OPS_RECOVERY_HISTORY_EXPORT` with export context (`limit/days/mode/count`).

### 2) Backend: security and endpoint tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden coverage for non-admin access to `/api/v1/ops/recovery/history/export`.
  - Added admin export test covering:
    - mode filter (`QUEUE_BY_WINDOW`),
    - CSV content type/header assertions,
    - CSV payload contains expected event type row.

### 3) Frontend: export action in diagnostics panel
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added `exportHistoryCsv(limit, days, mode?)` using `api.downloadFile`.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added `Export History CSV` button in **Ops Recovery Execution History** panel.
  - Exports with current window + current mode filter selection.
  - Added success/failure toasts:
    - `Ops recovery history CSV exported`
    - `Failed to export ops recovery history CSV`

### 4) Mock E2E: export behavior coverage
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Extended `**/api/v1/ops/recovery/history**` route handling:
    - list mode (`/history`)
    - export mode (`/history/export`) returning CSV attachment response.
  - Added assertions:
    - click export button successfully shows success toast,
    - export request carries selected mode (`DRY_RUN`).
