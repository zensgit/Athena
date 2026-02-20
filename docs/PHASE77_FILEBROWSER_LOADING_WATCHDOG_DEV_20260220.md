# Phase 77: FileBrowser Loading Watchdog and Recovery Actions

## Date
2026-02-20

## Background
- During folder browse startup, long-running or hanging folder-content requests may leave users in spinner-only state.
- Existing UI exposed retry only for explicit request errors, not for prolonged pending requests.

## Goal
1. Add a loading watchdog for FileBrowser pending states.
2. Provide explicit operator/user recovery actions when loading exceeds threshold.
3. Add mocked E2E coverage and include it in mocked regression gate.

## Changes

### 1) FileBrowser watchdog
- File: `ecm-frontend/src/pages/FileBrowser.tsx`
- Added configurable timeout:
  - `REACT_APP_FILE_BROWSER_LOADING_WATCHDOG_MS` (default `12000ms`)
- Added watchdog state:
  - `loadingStartedAt`
  - `loadingWatchdogTriggered`
- Added timeout effect for both startup-loading and folder-content loading.
- Added warning alert with actions (testable selectors):
  - `data-testid="filebrowser-loading-watchdog-alert"`
  - `data-testid="filebrowser-loading-watchdog-retry"`
  - `data-testid="filebrowser-loading-watchdog-back-root"`
- Recovery actions:
  - `Retry` => rerun folder load
  - `Back to root` => navigate to `/browse/root` (or retry when already at root)

### 2) Mocked E2E coverage
- File: `ecm-frontend/e2e/filebrowser-loading-watchdog.mock.spec.ts`
- Simulates hanging folder-content requests to trigger watchdog.
- Verifies alert/action visibility.
- Verifies recovery via retry reaches stable empty-folder state.
- Designed to be robust in both dev strict-mode and static runtime (initial hangs capped by request count).

### 3) Mocked regression gate integration
- File: `scripts/phase5-regression.sh`
- Added new spec to `PHASE5_SPECS`:
  - `e2e/filebrowser-loading-watchdog.mock.spec.ts`

## Non-Functional Notes
- No backend contract changes.
- Focused on browse startup recoverability and gate coverage.
