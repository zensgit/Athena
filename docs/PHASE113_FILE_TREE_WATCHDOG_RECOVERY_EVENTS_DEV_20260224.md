# Phase 113: FileBrowser/FolderTree Watchdog Recovery Event Coverage

## Date
2026-02-24

## Background
- File browser and folder tree watchdog mocked flows were covered by tests.
- These flows were not emitting structured `recovery_event` markers, so guard telemetry could not explicitly validate them.

## Goals
1. Emit recovery events for file browser watchdog recovery flow.
2. Emit recovery events for folder tree watchdog recovery flow.
3. Add these events to `phase5-regression` expected recovery event set.

## Changes

### 1) File browser watchdog markers
- `ecm-frontend/e2e/filebrowser-loading-watchdog.mock.spec.ts`
  - emits:
    - `recovery_event:filebrowser_watchdog_alert_shown`
    - `recovery_event:filebrowser_watchdog_retry_recovered`

### 2) Folder tree watchdog markers
- `ecm-frontend/e2e/folder-tree-root-watchdog.mock.spec.ts`
  - emits:
    - `recovery_event:folder_tree_watchdog_alert_shown`
    - `recovery_event:folder_tree_watchdog_retry_recovered`

### 3) Guard expected set expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `filebrowser_watchdog_alert_shown`
    - `filebrowser_watchdog_retry_recovered`
    - `folder_tree_watchdog_alert_shown`
    - `folder_tree_watchdog_retry_recovered`

## Impact
- No product runtime behavior change.
- Recovery guard coverage now includes file-browser and folder-tree watchdog recovery paths.
