# Phase 91: FolderTree Root Loading Watchdog

## Date
2026-02-22

## Background
- Recent resilience work reduced auth/search blank states, but folder tree bootstrap could still appear stuck when root loading is slow.
- Operator screenshots showed tree-area spinner/empty perception during root bootstrap latency spikes.

## Goals
1. Prevent silent long-loading states in folder tree root bootstrap.
2. Provide direct recovery actions in both slow-loading and load-failed states.
3. Cover the scenario in default mocked regression.

## Changes

### 1) Folder tree loading watchdog + retry
- File: `ecm-frontend/src/components/browser/FolderTree.tsx`
- Added:
  - `loadingWatchdogTriggered` state with timeout threshold.
  - warning alert + retry action during prolonged loading:
    - `data-testid="folder-tree-loading-watchdog-alert"`
    - `data-testid="folder-tree-loading-watchdog-retry"`
  - stable loading shell selector:
    - `data-testid="folder-tree-loading-state"`
- Timeout source:
  - env: `REACT_APP_FOLDER_TREE_LOADING_WATCHDOG_MS` (default `12000`)
  - test override key: `ecm_e2e_folder_tree_watchdog_ms`

### 2) Root-load failure fallback with explicit recovery
- File: `ecm-frontend/src/components/browser/FolderTree.tsx`
- Added:
  - dedicated load error state with retry action:
    - `data-testid="folder-tree-load-error"`
    - `data-testid="folder-tree-load-error-retry"`

### 3) Mocked E2E regression coverage
- File: `ecm-frontend/e2e/folder-tree-root-watchdog.mock.spec.ts`
- Coverage:
  - simulate slow root response window
  - assert watchdog alert appears
  - trigger retry and assert root tree item becomes visible
- Regression gate integration:
  - `scripts/phase5-regression.sh` includes this spec by default.

## Impact
- No backend API contract changes.
- Improves UI recoverability for folder bootstrap latency/failure.
- Expands mocked regression coverage for folder-tree startup resilience.
