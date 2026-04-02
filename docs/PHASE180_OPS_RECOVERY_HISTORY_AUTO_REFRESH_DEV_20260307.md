# Phase 180 - Ops Recovery History Auto Refresh (Development)

## Date
2026-03-07

## Goal
- Reduce manual refresh burden for operators monitoring active recovery windows.
- Keep auto-refresh optional and configurable to avoid unnecessary polling.

## Implemented

### 1) Frontend: auto refresh controls for history panel
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added auto-refresh state:
    - `recoveryHistoryAutoRefresh` (`on/off`)
    - `recoveryHistoryAutoRefreshSeconds` (`15s/30s/60s`)
  - Added timer effect:
    - when enabled, periodically triggers `loadFailures()`
    - timer is cleared on dependency change/unmount.
  - Added UI controls in **Ops Recovery Execution History** panel:
    - interval selector (`Ops recovery auto refresh seconds`)
    - toggle button (`Auto Refresh Off/On`)
  - Added active status chip (`Auto refresh <Ns>`).

### 2) Mock E2E update
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added assertion that auto-refresh toggle is visible in history panel.
  - Existing history mode/actor/eventType/export assertions remain intact.

## Notes
- Auto refresh currently reuses the existing diagnostics reload path to keep behavior consistent.
- Backend APIs are unchanged in this phase.
